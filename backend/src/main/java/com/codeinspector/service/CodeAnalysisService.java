package com.codeinspector.service;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSONObject;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.codeinspector.model.entity.CodeChunk;
import com.codeinspector.model.entity.CodeFile;
import com.codeinspector.mapper.CodeChunkMapper;
import com.codeinspector.mapper.CodeFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 代码分析服务 - JavaParser AST解析 + 智能切片
 * 核心：提取类、方法等结构化特征，实现代码智能切片以突破Token限制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAnalysisService {

    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final JavaParser javaParser = new JavaParser();

    @Value("${review.max-tokens-per-chunk:4000}")
    private int maxTokensPerChunk;

    @Value("${review.chunk-overlap:200}")
    private int chunkOverlap;

    @Value("${review.supported-extensions:java,py,js,ts,go}")
    private String supportedExtensions;

    private static final int CHARS_PER_TOKEN = 4; // 粗略估算

    /**
     * 分析并保存代码文件（支持整个项目目录）
     */
    public List<CodeFile> analyzeProjectCode(Long projectId, String projectPath) {
        File root = new File(projectPath);
        if (!root.exists()) {
            throw new IllegalArgumentException("项目路径不存在: " + projectPath);
        }

        List<CodeFile> codeFiles = new ArrayList<>();
        Set<String> extensions = Arrays.stream(supportedExtensions.split(","))
                .map(String::trim)
                .map(e -> e.startsWith(".") ? e : "." + e)
                .collect(Collectors.toSet());

        List<File> sourceFiles = FileUtil.loopFiles(root, file ->
                extensions.contains("." + FileUtil.extName(file).toLowerCase()));

        for (File sourceFile : sourceFiles) {
            try {
                String content = FileUtil.readString(sourceFile, StandardCharsets.UTF_8);
                String relativePath = sourceFile.getAbsolutePath()
                        .substring(root.getAbsolutePath().length() + 1);

                CodeFile codeFile = new CodeFile();
                codeFile.setProjectId(projectId);
                codeFile.setFilePath(relativePath);
                codeFile.setFileName(sourceFile.getName());
                codeFile.setFileContent(content);
                codeFile.setLineCount(content.split("\n").length);
                codeFileMapper.insert(codeFile);

                // 如果是Java文件，进行AST解析
                if (sourceFile.getName().endsWith(".java")) {
                    codeFile.setAstData(parseJavaAST(content));
                } else {
                    codeFile.setAstData("{}");
                }

                // 智能切片
                List<CodeChunk> chunks = createCodeChunks(codeFile, content);
                codeFile.setChunkCount(chunks.size());
                codeFileMapper.updateById(codeFile);

                // 保存切片
                for (CodeChunk chunk : chunks) {
                    chunk.setProjectId(projectId);
                    codeChunkMapper.insert(chunk);
                }

                codeFiles.add(codeFile);
                log.info("已分析文件: {} -> {} chunks", relativePath, chunks.size());
            } catch (Exception e) {
                log.error("分析文件失败: {}", sourceFile.getAbsolutePath(), e);
            }
        }

        return codeFiles;
    }

    /**
     * Java AST解析 - 提取类、方法、字段等结构化特征
     */
    public String parseJavaAST(String code) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(code);
            if (!result.isSuccessful()) {
                log.warn("JavaParser解析部分失败: {}", result.getProblems());
                return "{}";
            }

            CompilationUnit cu = result.getResult().orElse(null);
            if (cu == null) return "{}";

            JSONObject astData = new JSONObject();
            JSONObject classes = new JSONObject();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                JSONObject clsInfo = new JSONObject();
                clsInfo.put("name", cls.getNameAsString());
                clsInfo.put("type", cls.isInterface() ? "interface" : "class");
                clsInfo.put("lineStart", cls.getBegin().map(r -> r.line).orElse(-1));
                clsInfo.put("lineEnd", cls.getEnd().map(r -> r.line).orElse(-1));
                clsInfo.put("modifiers", cls.getModifiers().stream()
                        .map(m -> m.getKeyword().asString()).collect(Collectors.toList()));

                // 提取注解
                List<String> annotations = cls.getAnnotations().stream()
                        .map(a -> a.getNameAsString()).collect(Collectors.toList());
                clsInfo.put("annotations", annotations);

                // 提取方法
                List<JSONObject> methods = new ArrayList<>();
                cls.getMethods().forEach(method -> {
                    JSONObject mInfo = new JSONObject();
                    mInfo.put("name", method.getNameAsString());
                    mInfo.put("returnType", method.getType().asString());
                    mInfo.put("lineStart", method.getBegin().map(r -> r.line).orElse(-1));
                    mInfo.put("lineEnd", method.getEnd().map(r -> r.line).orElse(-1));
                    mInfo.put("parameters", method.getParameters().stream()
                            .map(p -> p.getType().asString() + " " + p.getNameAsString())
                            .collect(Collectors.toList()));
                    mInfo.put("modifiers", method.getModifiers().stream()
                            .map(m -> m.getKeyword().asString()).collect(Collectors.toList()));
                    mInfo.put("annotations", method.getAnnotations().stream()
                            .map(a -> a.getNameAsString()).collect(Collectors.toList()));
                    // 估算方法体大小
                    mInfo.put("bodyLineCount",
                            method.getBody().map(b -> b.getEnd().map(e -> e.line).orElse(0)
                                    - b.getBegin().map(s -> s.line).orElse(0)).orElse(0));
                    methods.add(mInfo);
                });
                clsInfo.put("methods", methods);

                // 提取字段
                List<JSONObject> fields = new ArrayList<>();
                cls.getFields().forEach(field -> {
                    field.getVariables().forEach(var -> {
                        JSONObject fInfo = new JSONObject();
                        fInfo.put("name", var.getNameAsString());
                        fInfo.put("type", var.getType().asString());
                        fInfo.put("line", field.getBegin().map(r -> r.line).orElse(-1));
                        fields.add(fInfo);
                    });
                });
                clsInfo.put("fields", fields);

                classes.put(cls.getNameAsString(), clsInfo);
            });

            astData.put("classes", classes);
            astData.put("package", cu.getPackageDeclaration()
                    .map(p -> p.getNameAsString()).orElse(""));
            astData.put("imports", cu.getImports().stream()
                    .map(i -> i.getNameAsString()).collect(Collectors.toList()));

            return astData.toJSONString();
        } catch (Exception e) {
            log.error("AST解析异常: ", e);
            return "{}";
        }
    }

    /**
     * 代码智能切片 - 核心算法
     * 策略：
     * 1. 基于AST结构切片（类/方法级别）
     * 2. 如果方法过大，按逻辑块切片
     * 3. 保持合理重叠，避免上下文丢失
     */
    public List<CodeChunk> createCodeChunks(CodeFile codeFile, String content) {
        List<CodeChunk> chunks = new ArrayList<>();

        // 如果是Java文件，基于AST进行结构化切片
        if (codeFile.getFileName().endsWith(".java")) {
            try {
                ParseResult<CompilationUnit> result = javaParser.parse(content);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    chunks = createASTBasedChunks(codeFile, content, result.getResult().get());
                    if (!chunks.isEmpty()) return chunks;
                }
            } catch (Exception e) {
                log.warn("AST切片失败，使用基于行的切片: {}", e.getMessage());
            }
        }

        // 降级：基于行的切片
        return createLineBasedChunks(codeFile, content);
    }

    /**
     * 基于AST结构的智能切片
     */
    private List<CodeChunk> createASTBasedChunks(CodeFile codeFile, String content, CompilationUnit cu) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        int chunkIndex = 0;

        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            int clsStartLine = cls.getBegin().map(r -> r.line).orElse(1);
            int clsEndLine = cls.getEnd().map(r -> r.line).orElse(lines.length);
            String clsName = cls.getNameAsString();

            // 类级别切片（包含import + 类声明 + 字段）
            StringBuilder clsChunk = new StringBuilder();
            // 添加package和import行
            for (int i = 0; i < clsStartLine - 1 && i < lines.length; i++) {
                clsChunk.append(lines[i]).append("\n");
            }
            clsChunk.append("// Class: ").append(clsName).append("\n");
            // 添加类声明和字段
            for (int i = clsStartLine - 1; i < Math.min(clsEndLine, lines.length); i++) {
                clsChunk.append(lines[i]).append("\n");
            }

            CodeChunk classChunk = buildChunk(codeFile.getId(), chunkIndex++, "CLASS", clsName,
                    clsChunk.toString(), clsStartLine, clsEndLine);
            chunks.add(classChunk);

            // 方法级别切片
            for (MethodDeclaration method : cls.getMethods()) {
                int methodStart = method.getBegin().map(r -> r.line).orElse(clsStartLine);
                int methodEnd = method.getEnd().map(r -> r.line).orElse(clsEndLine);
                String methodName = method.getNameAsString();

                // 构建方法切片（包含上下文）
                StringBuilder methodChunk = new StringBuilder();
                methodChunk.append("// Class: ").append(clsName).append(", Method: ").append(methodName).append("\n");

                // 添加上下文行（方法签名前的字段/注解）
                int contextStart = Math.max(methodStart - 3, clsStartLine);
                for (int i = contextStart - 1; i < Math.min(methodEnd, lines.length); i++) {
                    methodChunk.append(lines[i]).append("\n");
                }
                String chunkContent = methodChunk.toString();

                // 如果方法体超过Token限制，进一步切片
                if (chunkContent.length() > maxTokensPerChunk * CHARS_PER_TOKEN) {
                    chunks.addAll(splitLargeMethod(codeFile.getId(), chunkIndex, clsName, methodName,
                            methodStart, methodEnd, lines));
                    chunkIndex += Math.ceil((double) (methodEnd - methodStart) /
                            (maxTokensPerChunk * CHARS_PER_TOKEN / 100));
                } else {
                    CodeChunk methodChunkObj = buildChunk(codeFile.getId(), chunkIndex++, "METHOD",
                            clsName + "." + methodName, chunkContent, methodStart, methodEnd);
                    chunks.add(methodChunkObj);
                }
            }
        }

        // 如果没有找到类，回退
        if (chunks.isEmpty() && cu.findAll(ClassOrInterfaceDeclaration.class).isEmpty()) {
            return createLineBasedChunks(codeFile, content);
        }

        return chunks;
    }

    /**
     * 拆分超大方法
     */
    private List<CodeChunk> splitLargeMethod(Long fileId, int startChunkIndex,
                                              String clsName, String methodName,
                                              int methodStart, int methodEnd, String[] lines) {
        List<CodeChunk> subChunks = new ArrayList<>();
        int chunkLines = maxTokensPerChunk * CHARS_PER_TOKEN / 80; // 假设每行约80字符
        int overlapLines = chunkOverlap / 80;

        int idx = startChunkIndex;
        for (int i = methodStart - 1; i < methodEnd; i += (chunkLines - overlapLines)) {
            int end = Math.min(i + chunkLines, methodEnd);
            StringBuilder sb = new StringBuilder();
            sb.append("// Class: ").append(clsName).append(", Method: ").append(methodName)
                    .append(" [Part ").append(idx - startChunkIndex + 1).append("]\n");
            for (int j = i; j < end; j++) {
                sb.append(lines[j]).append("\n");
            }
            subChunks.add(buildChunk(fileId, idx++, "METHOD_PART",
                    clsName + "." + methodName + "#part" + (idx - startChunkIndex),
                    sb.toString(), i + 1, end));
        }
        return subChunks;
    }

    /**
     * 基于行的降级切片
     */
    private List<CodeChunk> createLineBasedChunks(CodeFile codeFile, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        int chunkLines = maxTokensPerChunk * CHARS_PER_TOKEN / 80;
        int overlapLines = chunkOverlap / 80;

        for (int i = 0; i < lines.length; i += (chunkLines - overlapLines)) {
            int end = Math.min(i + chunkLines, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(lines[j]).append("\n");
            }
            chunks.add(buildChunk(codeFile.getId(), i / (chunkLines - overlapLines),
                    "BLOCK", codeFile.getFileName() + "#L" + (i + 1),
                    sb.toString(), i + 1, end));
        }
        return chunks;
    }

    private CodeChunk buildChunk(Long fileId, int index, String type, String name,
                                  String content, int startLine, int endLine) {
        CodeChunk chunk = new CodeChunk();
        chunk.setFileId(fileId);
        chunk.setChunkIndex(index);
        chunk.setChunkType(type);
        chunk.setElementName(name);
        chunk.setChunkContent(content);
        chunk.setStartLine(startLine);
        chunk.setEndLine(endLine);
        return chunk;
    }

    /**
     * 获取项目的所有切片
     */
    public List<CodeChunk> getProjectChunks(Long projectId) {
        return codeChunkMapper.findByProjectId(projectId);
    }
}
