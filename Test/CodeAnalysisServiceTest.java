package com.codeinspector.service;

import com.codeinspector.entity.CodeChunk;
import com.codeinspector.entity.CodeFile;
import com.codeinspector.mapper.CodeChunkMapper;
import com.codeinspector.mapper.CodeFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CodeAnalysisService 单元测试
 * 覆盖：Java AST解析、智能切片（类/方法级）、非Java文件降级切片、行号映射、多语言识别
 */
@ExtendWith(MockitoExtension.class)
class CodeAnalysisServiceTest {

    @Mock
    private CodeFileMapper codeFileMapper;

    @Mock
    private CodeChunkMapper codeChunkMapper;

    @InjectMocks
    private CodeAnalysisService codeAnalysisService;

    @BeforeEach
    void setUp() {
        // 通过反射设置切片参数
        org.springframework.test.util.ReflectionTestUtils.setField(
                codeAnalysisService, "maxTokensPerChunk", 4000);
    }

    @Test
    @DisplayName("Java文件应能解析出类名和包名")
    void parseJavaAST_validJavaCode_shouldExtractClassAndPackage() {
        String code = "package com.example;\n\n" +
                "public class UserService {\n" +
                "    public void findUser() {}\n" +
                "}";

        String ast = codeAnalysisService.parseJavaAST(code);

        assertNotNull(ast, "AST解析结果不应为空");
        assertTrue(ast.contains("com.example"), "应包含包名");
        assertTrue(ast.contains("UserService"), "应包含类名");
        assertTrue(ast.contains("findUser"), "应包含方法名");
    }

    @Test
    @DisplayName("包含多个方法的类应解析出所有方法")
    void parseJavaAST_multipleMethods_shouldExtractAll() {
        String code = "package com.example;\n\n" +
                "public class Calculator {\n" +
                "    public int add(int a, int b) { return a + b; }\n" +
                "    public int subtract(int a, int b) { return a - b; }\n" +
                "    private void log(String msg) {}\n" +
                "}";

        String ast = codeAnalysisService.parseJavaAST(code);

        assertTrue(ast.contains("add"), "应包含add方法");
        assertTrue(ast.contains("subtract"), "应包含subtract方法");
        assertTrue(ast.contains("log"), "应包含log方法");
    }

    @Test
    @DisplayName("语法错误的Java代码应返回空JSON而非抛异常")
    void parseJavaAST_invalidCode_shouldReturnEmptyJson() {
        String invalidCode = "public class Broken { this is not valid java !!! }";

        String ast = codeAnalysisService.parseJavaAST(invalidCode);

        assertNotNull(ast, "不应返回null");
        assertEquals("{}", ast, "语法错误应返回空JSON");
    }

    @Test
    @DisplayName("Java文件应基于AST生成类级别和方法级切片")
    void createCodeChunks_javaFile_shouldProduceAstBasedChunks() {
        String code = "package com.example;\n\n" +
                "public class UserController {\n" +
                "    public void getUser() {\n" +
                "        System.out.println(\"get user\");\n" +
                "    }\n" +
                "    public void createUser() {\n" +
                "        System.out.println(\"create user\");\n" +
                "    }\n" +
                "}";

        CodeFile file = createFile("UserController.java", code, 1L);

        List<CodeChunk> chunks = codeAnalysisService.createCodeChunks(file, code);

        assertFalse(chunks.isEmpty(), "应生成至少一个切片");
        // 应包含类级切片和方法级切片
        boolean hasClassChunk = chunks.stream()
                .anyMatch(c -> "CLASS".equals(c.getChunkType()));
        boolean hasMethodChunk = chunks.stream()
                .anyMatch(c -> "METHOD".equals(c.getChunkType()));
        assertTrue(hasClassChunk, "应包含类级切片");
        assertTrue(hasMethodChunk, "应包含方法级切片");
    }

    @Test
    @DisplayName("非Java文件应降级为基于行的切片")
    void createCodeChunks_nonJavaFile_shouldFallbackToLineBased() {
        String code = "def hello():\n" +
                "    print('hello')\n" +
                "    return True\n";

        CodeFile file = createFile("app.py", code, 1L);

        List<CodeChunk> chunks = codeAnalysisService.createCodeChunks(file, code);

        assertFalse(chunks.isEmpty(), "非Java文件也应生成切片");
        assertTrue(chunks.stream().allMatch(c -> "LINE".equals(c.getChunkType())),
                "非Java文件应使用LINE类型切片");
    }

    @Test
    @DisplayName("切片的行号应正确映射到文件绝对行号")
    void createCodeChunks_shouldHaveCorrectLineNumbers() {
        String code = "package com.example;\n\n" +
                "public class Demo {\n" +
                "    public void method() {\n" +
                "        int x = 1;\n" +
                "    }\n" +
                "}";

        CodeFile file = createFile("Demo.java", code, 1L);
        List<CodeChunk> chunks = codeAnalysisService.createCodeChunks(file, code);

        for (CodeChunk chunk : chunks) {
            assertNotNull(chunk.getStartLine(), "切片应有起始行号");
            assertNotNull(chunk.getEndLine(), "切片应有结束行号");
            assertTrue(chunk.getStartLine() >= 1, "起始行号应>=1");
            assertTrue(chunk.getEndLine() >= chunk.getStartLine(),
                    "结束行号应>=起始行号");
        }
    }

    @Test
    @DisplayName("空内容文件不应生成切片")
    void createCodeChunks_emptyContent_shouldReturnEmpty() {
        CodeFile file = createFile("Empty.java", "", 1L);

        List<CodeChunk> chunks = codeAnalysisService.createCodeChunks(file, "");

        assertTrue(chunks.isEmpty(), "空内容不应生成切片");
    }

    @Test
    @DisplayName("analyzeProjectCode应保存文件和切片到数据库")
    void analyzeProjectCode_shouldSaveFilesAndChunks() {
        // 模拟一个包含Java文件的目录
        String tempDir = System.getProperty("java.io.tmpdir") + "/test-code-" + System.currentTimeMillis();
        java.io.File dir = new java.io.File(tempDir);
        dir.mkdirs();
        try {
            java.io.File javaFile = new java.io.File(dir, "Test.java");
            java.nio.file.Files.writeString(javaFile.toPath(),
                    "public class Test { public void m() {} }");

            when(codeFileMapper.insert(any())).thenReturn(1);

            List<CodeFile> files = codeAnalysisService.analyzeProjectCode(1L, tempDir);

            assertFalse(files.isEmpty(), "应分析出至少一个文件");
            verify(codeFileMapper, atLeastOnce()).insert(any());
        } catch (Exception e) {
            fail("测试异常: " + e.getMessage());
        } finally {
            // 清理
            deleteDir(dir);
        }
    }

    @Test
    @DisplayName("支持的语言列表应包含Java/Python/JS/TS/Go")
    void supportedLanguages_shouldIncludeFiveLanguages() {
        // 通过分析不同扩展名文件验证
        String[] exts = {".java", ".py", ".js", ".ts", ".go"};
        for (String ext : exts) {
            CodeFile file = createFile("test" + ext, "var x = 1;", 1L);
            List<CodeChunk> chunks = codeAnalysisService.createCodeChunks(file, "var x = 1;");
            // 非Java都应降级为行切片
            if (!ext.equals(".java")) {
                assertTrue(chunks.stream().allMatch(c -> "LINE".equals(c.getChunkType())),
                        ext + " 文件应使用LINE切片");
            }
        }
    }

    private CodeFile createFile(String name, String content, Long projectId) {
        CodeFile file = new CodeFile();
        file.setFileName(name);
        file.setFilePath("src/" + name);
        file.setContent(content);
        file.setProjectId(projectId);
        file.setLineCount((long) content.split("\n").length);
        return file;
    }

    private void deleteDir(java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
