package com.codeinspector.service;

import com.codeinspector.common.BusinessException;
import com.codeinspector.mapper.CodeFileMapper;
import com.codeinspector.mapper.ProjectMapper;
import com.codeinspector.model.entity.CodeFile;
import com.codeinspector.model.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码风格画像服务
 * 基于规则静态扫描项目代码，提取用户的编码风格特征（缩进、括号、命名、注释、日志、框架使用等），
 * 生成文本画像，用于在 AI 审查时注入 prompt，使审查建议和修复代码贴合用户既有风格。
 *
 * 设计为纯规则统计，不依赖 AI 调用：快速、确定性、零额外成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeStyleService {

    private final CodeFileMapper codeFileMapper;
    private final ProjectMapper projectMapper;

    // 采样上限，避免超大项目扫描过久
    private static final int MAX_FILES = 80;
    private static final int MAX_CHARS_PER_FILE = 60_000;

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    // 方法定义：修饰符 + 返回类型 + 方法名(
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected)\\s+" +
            "(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+|default\\s+|native\\s+)*" +
            "(?:<[^>]+>\\s+)?" +
            "[A-Za-z_][A-Za-z0-9_<>,\\[\\]\\s\\?\\.]*?\\s+" +
            "([a-z_][A-Za-z0-9_]*)\\s*\\(");
    // 常量定义：static final 类型 NAME =
    private static final Pattern CONST_PATTERN = Pattern.compile(
            "static\\s+final\\s+[\\w<>\\[\\],\\s\\?\\.]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)(\\.\\*)?\\s*;");

    private static final List<String> FRAMEWORK_ANNOTATIONS = List.of(
            "@RestController", "@Controller", "@Service", "@Repository", "@Component",
            "@Configuration", "@Bean", "@Autowired", "@Resource", "@Inject",
            "@RequestMapping", "@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping", "@PatchMapping",
            "@Data", "@Slf4j", "@Builder", "@AllArgsConstructor", "@NoArgsConstructor",
            "@RequiredArgsConstructor", "@Value", "@Transactional", "@Async", "@Override"
    );

    /**
     * 分析项目代码风格并持久化画像
     */
    public String analyzeAndSaveStyle(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        List<CodeFile> files = codeFileMapper.findByProjectId(projectId);
        String profile = buildStyleProfile(files);
        project.setStyleProfile(profile);
        project.setStyleAnalyzedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        log.info("项目[{}]代码风格画像已生成", projectId);
        return profile;
    }

    /**
     * 获取已有画像；若尚未分析则现场分析
     */
    public String getOrAnalyzeStyle(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (project.getStyleProfile() != null && !project.getStyleProfile().isBlank()) {
            return project.getStyleProfile();
        }
        return analyzeAndSaveStyle(projectId);
    }

    /**
     * 根据文件列表构建风格画像文本
     */
    public String buildStyleProfile(List<CodeFile> files) {
        if (files == null || files.isEmpty()) {
            return "（项目中暂无代码文件，无法提取风格画像）";
        }

        // 采样
        List<CodeFile> sample = new ArrayList<>();
        for (CodeFile f : files) {
            if (sample.size() >= MAX_FILES) break;
            if (f.getFileContent() != null && !f.getFileContent().isBlank()) {
                sample.add(f);
            }
        }

        // ---- 统计计数器 ----
        long totalLines = 0;
        long nonBlankLines = 0;
        long tabIndent = 0, space2Indent = 0, space4Indent = 0, otherIndent = 0;
        long braceSameLine = 0, braceNextLine = 0;
        long maxLineLen = 0, totalLineLen = 0, linesOver120 = 0;
        long singleLineComments = 0, blockCommentLines = 0, javadocBlocks = 0;
        long systemOutCount = 0, slf4jCallCount = 0, lombokSlf4jCount = 0;
        long wildcardImports = 0, totalImports = 0;
        long thisPrefixCount = 0, fieldAssignCount = 0;
        long multiVarDecl = 0;

        long classCount = 0, classPascal = 0;
        long methodCount = 0, methodCamel = 0;
        long constCount = 0, constUpper = 0;

        Map<String, Long> frameworkHits = new HashMap<>();
        Map<String, Long> langCount = new HashMap<>();

        for (CodeFile f : sample) {
            String lang = detectLanguage(f.getFileName());
            langCount.merge(lang, 1L, Long::sum);

            String content = f.getFileContent();
            if (content.length() > MAX_CHARS_PER_FILE) {
                content = content.substring(0, MAX_CHARS_PER_FILE);
            }
            String[] lines = content.split("\n", -1);

            boolean inBlockComment = false;
            for (String rawLine : lines) {
                totalLines++;
                int len = rawLine.length();
                totalLineLen += len;
                if (len > maxLineLen) maxLineLen = len;
                if (len > 120) linesOver120++;

                String trimmed = rawLine.trim();
                if (trimmed.isEmpty()) continue;
                nonBlankLines++;

                // 缩进（只统计有实际缩进的行，顶层无缩进行不计入；
                // 多级缩进按其基准缩进归类：8/12空格归入4空格风格，6空格归入2空格风格）
                char first = rawLine.charAt(0);
                if (first == '\t') {
                    tabIndent++;
                } else if (first == ' ') {
                    int indent = 0;
                    while (indent < len && rawLine.charAt(indent) == ' ') indent++;
                    if (indent > 0) {
                        if (indent % 4 == 0) space4Indent++;
                        else if (indent % 2 == 0) space2Indent++;
                        else otherIndent++;
                    }
                }

                // 块注释状态
                if (inBlockComment) {
                    blockCommentLines++;
                    if (trimmed.contains("*/")) inBlockComment = false;
                    continue;
                }
                if (trimmed.startsWith("/*")) {
                    blockCommentLines++;
                    if (trimmed.startsWith("/**")) javadocBlocks++;
                    if (!trimmed.contains("*/")) inBlockComment = true;
                    continue;
                }
                if (trimmed.startsWith("//")) {
                    singleLineComments++;
                    continue;
                }

                // import
                Matcher im = IMPORT_PATTERN.matcher(trimmed);
                if (im.find()) {
                    totalImports++;
                    if (im.group(2) != null) wildcardImports++;
                }

                // 大括号位置
                if (rawLine.matches(".*\\)\\s*\\{\\s*$")) braceSameLine++;
                if (trimmed.startsWith("{")) braceNextLine++;

                // 日志
                if (trimmed.contains("System.out.print") || trimmed.contains("System.err.print")) systemOutCount++;
                if (trimmed.contains("log.") || trimmed.contains("logger.") || trimmed.contains("LOGGER.")) slf4jCallCount++;
                if (trimmed.startsWith("@Slf4j")) lombokSlf4jCount++;

                // 字段赋值 this. 前缀
                Matcher assign = Pattern.compile("^this\\.([a-zA-Z_]\\w*)\\s*=").matcher(trimmed);
                if (assign.find()) {
                    thisPrefixCount++;
                    String varName = assign.group(1);
                    // 粗略统计 setter 风格字段赋值
                    if (Character.isLowerCase(varName.charAt(0))) fieldAssignCount++;
                } else if (trimmed.matches("^[a-zA-Z_]\\w*\\s*=.*") && !trimmed.contains("==")) {
                    // 不带 this. 的赋值（仅作对比，不精确）
                }

                // 多变量声明 int a, b, c;
                if (trimmed.matches("^(?:final\\s+)?(?:int|long|short|byte|char|float|double|boolean|String|[A-Z]\\w*)\\s+[a-zA-Z_]\\w*(?:\\s*=\\s*[^,;]+)?(?:\\s*,\\s*[a-zA-Z_]\\w*)+;.*")) {
                    multiVarDecl++;
                }

                // 框架注解
                for (String ann : FRAMEWORK_ANNOTATIONS) {
                    if (trimmed.startsWith(ann) || trimmed.contains(" " + ann + " ") || trimmed.contains("(" + ann)) {
                        frameworkHits.merge(ann, 1L, Long::sum);
                    }
                }

                // 命名
                Matcher cm = CLASS_PATTERN.matcher(trimmed);
                if (cm.find()) {
                    classCount++;
                    String name = cm.group(2);
                    if (name.matches("[A-Z][A-Za-z0-9]*")) classPascal++;
                }
                Matcher mm = METHOD_PATTERN.matcher(trimmed);
                if (mm.find()) {
                    methodCount++;
                    String name = mm.group(1);
                    if (name.matches("[a-z][a-zA-Z0-9]*")) methodCamel++;
                }
                Matcher conm = CONST_PATTERN.matcher(trimmed);
                if (conm.find()) {
                    constCount++;
                    String name = conm.group(1);
                    if (name.matches("[A-Z][A-Z0-9_]*")) constUpper++;
                }
            }
        }

        return renderProfile(sample.size(), totalLines, nonBlankLines,
                tabIndent, space2Indent, space4Indent, otherIndent,
                braceSameLine, braceNextLine,
                maxLineLen, totalLineLen, linesOver120,
                singleLineComments, blockCommentLines, javadocBlocks,
                systemOutCount, slf4jCallCount, lombokSlf4jCount,
                wildcardImports, totalImports,
                thisPrefixCount, multiVarDecl,
                classCount, classPascal, methodCount, methodCamel, constCount, constUpper,
                frameworkHits, langCount);
    }

    private String detectLanguage(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".ts")) return "TypeScript";
        if (lower.endsWith(".js")) return "JavaScript";
        if (lower.endsWith(".go")) return "Go";
        return "other";
    }

    private String pct(long part, long total) {
        if (total <= 0) return "0%";
        return Math.round(part * 100.0 / total) + "%";
    }

    private String renderProfile(int fileCount, long totalLines, long nonBlankLines,
                                 long tabIndent, long space2Indent, long space4Indent, long otherIndent,
                                 long braceSameLine, long braceNextLine,
                                 long maxLineLen, long totalLineLen, long linesOver120,
                                 long singleLineComments, long blockCommentLines, long javadocBlocks,
                                 long systemOutCount, long slf4jCallCount, long lombokSlf4jCount,
                                 long wildcardImports, long totalImports,
                                 long thisPrefixCount, long multiVarDecl,
                                 long classCount, long classPascal,
                                 long methodCount, long methodCamel,
                                 long constCount, long constUpper,
                                 Map<String, Long> frameworkHits,
                                 Map<String, Long> langCount) {

        StringBuilder sb = new StringBuilder();
        sb.append("【代码风格画像】（基于项目中 ").append(fileCount).append(" 个源文件、")
          .append(totalLines).append(" 行代码自动分析）\n");

        // 语言
        sb.append("1. 主要语言：");
        langCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append("个文件) "));
        sb.append("\n");

        // 缩进
        sb.append("2. 缩进：");
        long indentTotal = tabIndent + space2Indent + space4Indent + otherIndent;
        if (indentTotal > 0) {
            Map<String, Long> indentMap = new LinkedHashMap<>();
            indentMap.put("4空格", space4Indent);
            indentMap.put("2空格", space2Indent);
            indentMap.put("Tab", tabIndent);
            indentMap.put("其他", otherIndent);
            indentMap.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> sb.append("以").append(e.getKey()).append("为主（占比 ")
                            .append(pct(e.getValue(), indentTotal)).append("）"));
        } else {
            sb.append("未检测到缩进特征");
        }
        sb.append("\n");

        // 大括号
        sb.append("3. 大括号风格：");
        if (braceSameLine + braceNextLine > 0) {
            if (braceSameLine >= braceNextLine * 2) {
                sb.append("K&R 同行风格（`) {`，同行 ").append(braceSameLine).append(" 处，下一行 ").append(braceNextLine).append(" 处）");
            } else if (braceNextLine > braceSameLine * 2) {
                sb.append("Allman 换行风格（`{` 独占一行，下一行 ").append(braceNextLine).append(" 处，同行 ").append(braceSameLine).append(" 处）");
            } else {
                sb.append("混合风格（同行 ").append(braceSameLine).append(" 处，下一行 ").append(braceNextLine).append(" 处）");
            }
        } else {
            sb.append("未检测到大括号特征");
        }
        sb.append("\n");

        // 行宽
        long avgLen = nonBlankLines > 0 ? totalLineLen / nonBlankLines : 0;
        sb.append("4. 行宽：平均约 ").append(avgLen).append(" 字符，最长 ").append(maxLineLen)
          .append(" 字符，超过120字符的行占比 ").append(pct(linesOver120, nonBlankLines)).append("\n");

        // 注释
        long commentTotal = singleLineComments + blockCommentLines;
        sb.append("5. 注释：行注释 ").append(singleLineComments).append(" 处，块注释行 ")
          .append(blockCommentLines).append("（其中 Javadoc 块 ").append(javadocBlocks)
          .append(" 个），注释行占比约 ").append(pct(commentTotal, totalLines)).append("\n");

        // 日志
        sb.append("6. 日志方式：");
        List<String> logParts = new ArrayList<>();
        if (lombokSlf4jCount > 0) logParts.add("Lombok @Slf4j（" + lombokSlf4jCount + " 处）");
        if (slf4jCallCount > 0) logParts.add("Slf4j log 调用（" + slf4jCallCount + " 处）");
        if (systemOutCount > 0) logParts.add("System.out/err（" + systemOutCount + " 处）");
        sb.append(logParts.isEmpty() ? "未检测到明显日志调用" : String.join("、", logParts));
        sb.append("\n");

        // import
        sb.append("7. Import：共 ").append(totalImports).append(" 条 import");
        if (wildcardImports > 0) {
            sb.append("，其中通配符 import ").append(wildcardImports).append(" 条");
        } else {
            sb.append("，全部为精确导入（无通配符 *）");
        }
        sb.append("\n");

        // 命名
        sb.append("8. 命名风格：");
        List<String> nameParts = new ArrayList<>();
        if (classCount > 0) nameParts.add("类/接口 PascalCase（" + classPascal + "/" + classCount + "）");
        if (methodCount > 0) nameParts.add("方法/变量 camelCase（" + methodCamel + "/" + methodCount + "）");
        if (constCount > 0) nameParts.add("常量 UPPER_SNAKE_CASE（" + constUpper + "/" + constCount + "）");
        sb.append(nameParts.isEmpty() ? "样本不足" : String.join("、", nameParts));
        sb.append("\n");

        // 字段访问
        sb.append("9. 字段访问：setter/构造器中使用 this. 前缀 ").append(thisPrefixCount).append(" 处");
        if (multiVarDecl > 0) sb.append("；存在多变量同一声明（").append(multiVarDecl).append(" 处）");
        sb.append("\n");

        // 框架
        sb.append("10. 框架/库特征：");
        if (frameworkHits.isEmpty()) {
            sb.append("未检测到特定框架注解");
        } else {
            List<String> fwParts = new ArrayList<>();
            frameworkHits.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(8)
                    .forEach(e -> fwParts.add(e.getKey() + "(" + e.getValue() + ")"));
            sb.append(String.join("、", fwParts));
        }
        sb.append("\n");

        return sb.toString();
    }
}
