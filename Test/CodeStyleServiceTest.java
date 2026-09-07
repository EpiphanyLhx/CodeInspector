package com.codeinspector.service;

import com.codeinspector.entity.CodeFile;
import com.codeinspector.entity.Project;
import com.codeinspector.mapper.CodeFileMapper;
import com.codeinspector.mapper.CodeChunkMapper;
import com.codeinspector.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CodeStyleService 单元测试
 * 覆盖：风格画像统计、缩进识别、大括号风格识别、命名规范统计、空文件处理
 */
@ExtendWith(MockitoExtension.class)
class CodeStyleServiceTest {

    @Mock
    private CodeFileMapper codeFileMapper;

    @Mock
    private CodeChunkMapper codeChunkMapper;

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private CodeStyleService codeStyleService;

    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = new Project();
        testProject.setId(1L);
        testProject.setName("test-project");
    }

    @Test
    @DisplayName("4空格缩进的代码应识别为space4风格")
    void buildStyleProfile_space4Indent_shouldDetectSpace4() {
        String code = "public class Demo {\n" +
                "    public void method() {\n" +
                "        int x = 1;\n" +
                "        System.out.println(x);\n" +
                "    }\n" +
                "}";
        CodeFile file = createFile("Demo.java", code);

        when(codeFileMapper.findByProjectId(1L)).thenReturn(Collections.singletonList(file));

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile, "风格画像不应为空");
        assertTrue(profile.contains("4空格"), "应识别为4空格缩进");
    }

    @Test
    @DisplayName("Tab缩进的代码应识别为tab风格")
    void buildStyleProfile_tabIndent_shouldDetectTab() {
        String code = "public class Demo {\n" +
                "\tpublic void method() {\n" +
                "\t\tint x = 1;\n" +
                "\t}\n" +
                "}";
        CodeFile file = createFile("Demo.java", code);

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile);
        assertTrue(profile.contains("Tab"), "应识别为Tab缩进");
    }

    @Test
    @DisplayName("K&R风格大括号（同行）应被正确识别")
    void buildStyleProfile_krBraceStyle_shouldDetectSameLine() {
        String code = "public class Demo {\n" +
                "    public void method() {\n" +
                "        if (true) {\n" +
                "            doSomething();\n" +
                "        }\n" +
                "    }\n" +
                "}";
        CodeFile file = createFile("Demo.java", code);

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile);
        assertTrue(profile.contains("K&R") || profile.contains("同行"),
                "应识别K&R大括号风格");
    }

    @Test
    @DisplayName("Allman风格大括号（换行）应被正确识别")
    void buildStyleProfile_allmanBraceStyle_shouldDetectNextLine() {
        String code = "public class Demo\n" +
                "{\n" +
                "    public void method()\n" +
                "    {\n" +
                "        int x = 1;\n" +
                "    }\n" +
                "}";
        CodeFile file = createFile("Demo.java", code);

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile);
        assertTrue(profile.contains("Allman") || profile.contains("换行"),
                "应识别Allman大括号风格");
    }

    @Test
    @DisplayName("PascalCase类名应被识别为符合规范")
    void buildStyleProfile_pascalCaseClass_shouldDetectCorrectNaming() {
        String code = "public class UserService {\n" +
                "    private String userName;\n" +
                "    public void getUserInfo() {}\n" +
                "}";
        CodeFile file = createFile("UserService.java", code);

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile);
        assertTrue(profile.contains("PascalCase"), "应识别PascalCase类命名");
    }

    @Test
    @DisplayName("常量UPPER_SNAKE_CASE应被识别")
    void buildStyleProfile_upperSnakeConstant_shouldDetect() {
        String code = "public class Config {\n" +
                "    public static final int MAX_SIZE = 100;\n" +
                "    public static final String DEFAULT_NAME = \"test\";\n" +
                "}";
        CodeFile file = createFile("Config.java", code);

        String profile = codeStyleService.buildStyleProfile(Collections.singletonList(file));

        assertNotNull(profile);
        assertTrue(profile.contains("UPPER_SNAKE_CASE") || profile.contains("大写下划线"),
                "应识别常量命名规范");
    }

    @Test
    @DisplayName("空文件列表应返回空画像而非null")
    void buildStyleProfile_emptyFileList_shouldReturnEmptyProfile() {
        String profile = codeStyleService.buildStyleProfile(Collections.emptyList());
        assertNotNull(profile, "空文件列表不应返回null");
    }

    @Test
    @DisplayName("非Java文件应被跳过")
    void buildStyleProfile_nonJavaFile_shouldBeSkipped() {
        CodeFile javaFile = createFile("App.java", "public class App {}");
        CodeFile txtFile = createFile("readme.txt", "just some text");

        String profile = codeStyleService.buildStyleProfile(Arrays.asList(javaFile, txtFile));

        assertNotNull(profile);
        // txt文件不应影响Java风格统计
    }

    @Test
    @DisplayName("analyzeAndSaveStyle应调用mapper更新项目")
    void analyzeAndSaveStyle_shouldUpdateProject() {
        CodeFile file = createFile("Demo.java", "public class Demo {\n    int x;\n}");
        when(codeFileMapper.findByProjectId(1L)).thenReturn(Collections.singletonList(file));
        when(projectMapper.selectById(1L)).thenReturn(testProject);

        codeStyleService.analyzeAndSaveStyle(1L);

        verify(projectMapper, times(1)).updateById(any(Project.class));
    }

    private CodeFile createFile(String name, String content) {
        CodeFile file = new CodeFile();
        file.setFileName(name);
        file.setFilePath("src/" + name);
        file.setContent(content);
        file.setLineCount((long) content.split("\n").length);
        return file;
    }
}
