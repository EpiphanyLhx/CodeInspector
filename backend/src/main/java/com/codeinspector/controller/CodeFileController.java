package com.codeinspector.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.Result;
import com.codeinspector.mapper.*;
import com.codeinspector.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeFileController {

    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final ProjectMapper projectMapper;
    private final ReviewIssueMapper reviewIssueMapper;

    @GetMapping("/projects/{projectId}/files")
    public Result<List<CodeFile>> getFiles(@PathVariable Long projectId) {
        List<CodeFile> files = codeFileMapper.findByProjectId(projectId);
        files.forEach(f -> f.setFileContent(null));
        return Result.success(files);
    }

    @GetMapping("/files/{fileId}")
    public Result<Map<String, Object>> getFileContent(@PathVariable Long fileId) {
        CodeFile file = codeFileMapper.selectById(fileId);
        if (file == null) return Result.error("文件不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", file.getId());
        data.put("fileName", file.getFileName());
        data.put("filePath", file.getFilePath());
        data.put("content", file.getFileContent());
        data.put("language", getLanguage(file.getFileName()));
        return Result.success(data);
    }

    @GetMapping("/projects/{projectId}/chunks")
    public Result<List<CodeChunk>> getChunks(@PathVariable Long projectId) {
        return Result.success(codeChunkMapper.findByProjectId(projectId));
    }

    /**
     * 获取用户审查历史
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) String status) {
        // 获取所有项目（不限审查状态）
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().orderByDesc(Project::getCreateTime));
        if (userId != null) {
            projects = projects.stream().filter(p -> p.getCreatorId().equals(userId)).collect(Collectors.toList());
        }
        // 按状态筛选
        if (status != null && !status.isEmpty()) {
            projects = projects.stream().filter(p -> status.equals(p.getReviewStatus())).collect(Collectors.toList());
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (Project project : projects) {
            List<ReviewIssue> issues = reviewIssueMapper.findByProjectId(project.getId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("projectId", project.getId());
            entry.put("projectName", project.getName());
            entry.put("language", project.getLanguage());
            entry.put("sourceType", project.getSourceType());
            entry.put("reviewStatus", project.getReviewStatus());
            entry.put("reviewDate", project.getUpdateTime());
            entry.put("createDate", project.getCreateTime());
            entry.put("files", project.getTotalFiles());
            entry.put("lines", project.getTotalLines());
            entry.put("issues", issues.size());
            entry.put("critical", issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count());
            entry.put("major", issues.stream().filter(i -> "MAJOR".equals(i.getSeverity())).count());
            history.add(entry);
        }
        return Result.success(history);
    }

    /**
     * 删除历史记录（级联删除项目及关联数据）
     */
    @DeleteMapping("/history/{projectId}")
    public Result<Void> deleteHistory(@PathVariable Long projectId) {
        // 删除关联数据
        reviewIssueMapper.delete(new LambdaQueryWrapper<ReviewIssue>().eq(ReviewIssue::getProjectId, projectId));
        codeChunkMapper.delete(new LambdaQueryWrapper<CodeChunk>().eq(CodeChunk::getProjectId, projectId));
        codeFileMapper.delete(new LambdaQueryWrapper<CodeFile>().eq(CodeFile::getProjectId, projectId));
        projectMapper.deleteById(projectId);
        return Result.success();
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
