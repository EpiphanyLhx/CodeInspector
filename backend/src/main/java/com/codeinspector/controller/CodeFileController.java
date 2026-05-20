package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.mapper.CodeFileMapper;
import com.codeinspector.mapper.CodeChunkMapper;
import com.codeinspector.model.entity.CodeFile;
import com.codeinspector.model.entity.CodeChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeFileController {

    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;

    /**
     * 获取项目的所有文件列表
     */
    @GetMapping("/projects/{projectId}/files")
    public Result<List<CodeFile>> getFiles(@PathVariable Long projectId) {
        List<CodeFile> files = codeFileMapper.findByProjectId(projectId);
        // 不返回完整内容，减少传输量
        files.forEach(f -> f.setFileContent(null));
        return Result.success(files);
    }

    /**
     * 获取单个文件内容（用于Monaco Editor展示）
     */
    @GetMapping("/files/{fileId}")
    public Result<Map<String, Object>> getFileContent(@PathVariable Long fileId) {
        CodeFile file = codeFileMapper.selectById(fileId);
        if (file == null) {
            return Result.error("文件不存在");
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", file.getId());
        data.put("fileName", file.getFileName());
        data.put("filePath", file.getFilePath());
        data.put("content", file.getFileContent());
        data.put("language", getLanguage(file.getFileName()));
        return Result.success(data);
    }

    /**
     * 获取项目的所有切片
     */
    @GetMapping("/projects/{projectId}/chunks")
    public Result<List<CodeChunk>> getChunks(@PathVariable Long projectId) {
        return Result.success(codeChunkMapper.findByProjectId(projectId));
    }

    private String getLanguage(String fileName) {
        if (fileName == null) return "plaintext";
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".js")) return "javascript";
        if (fileName.endsWith(".ts")) return "typescript";
        if (fileName.endsWith(".go")) return "go";
        if (fileName.endsWith(".xml")) return "xml";
        if (fileName.endsWith(".json")) return "json";
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) return "yaml";
        if (fileName.endsWith(".sql")) return "sql";
        return "plaintext";
    }
}
