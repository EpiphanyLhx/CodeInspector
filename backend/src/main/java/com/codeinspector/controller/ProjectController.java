package com.codeinspector.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeinspector.common.Result;
import com.codeinspector.model.dto.CreateProjectDTO;
import com.codeinspector.model.entity.Project;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.ProjectVO;
import com.codeinspector.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public Result<Project> create(@Valid @RequestBody CreateProjectDTO dto,
                                   @AuthenticationPrincipal User user) {
        return Result.success(projectService.createProject(dto, user.getId()));
    }

    @GetMapping
    public Result<Page<ProjectVO>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(projectService.getUserProjects(user.getId(), page, size));
    }

    @GetMapping("/{projectId}")
    public Result<ProjectVO> detail(@PathVariable Long projectId) {
        return Result.success(projectService.getProjectDetail(projectId));
    }

    @PostMapping("/{projectId}/upload")
    public Result<Project> uploadCode(@PathVariable Long projectId,
                                       @RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal User user) throws IOException {
        return Result.success(projectService.uploadCode(projectId, file, user.getId()));
    }

    @PostMapping("/{projectId}/git-pull")
    public Result<Project> pullFromGit(@PathVariable Long projectId,
                                        @AuthenticationPrincipal User user) {
        return Result.success(projectService.pullFromGit(projectId, user.getId()));
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> delete(@PathVariable Long projectId,
                                @AuthenticationPrincipal User user) {
        projectService.deleteProject(projectId, user.getId());
        return Result.success();
    }

    @DeleteMapping("/{projectId}/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long projectId,
                                    @PathVariable Long fileId,
                                    @AuthenticationPrincipal User user) {
        projectService.deleteFile(projectId, fileId, user.getId());
        return Result.success();
    }
}
