package com.codeinspector.service;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeinspector.common.BusinessException;
import com.codeinspector.model.dto.CreateProjectDTO;
import com.codeinspector.model.entity.*;
import com.codeinspector.model.vo.ProjectVO;
import com.codeinspector.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final ReviewReportMapper reviewReportMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final CodeAnalysisService codeAnalysisService;
    private final ReviewService reviewService;
    private final GitService gitService;

    @Value("${file.upload-dir:/tmp/code-inspector/uploads}")
    private String uploadDir;

    /**
     * 创建项目
     */
    @Transactional
    public Project createProject(CreateProjectDTO dto, Long userId) {
        // 如果指定了团队，检查团队权限
        if (dto.getTeamId() != null) {
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, dto.getTeamId())
                    .eq(TeamMember::getUserId, userId));
            if (member == null) {
                throw new BusinessException("你不是该团队的成员");
            }
        }

        Project project = new Project();
        project.setTeamId(dto.getTeamId());
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setSourceType(dto.getSourceType());
        project.setGitUrl(dto.getGitUrl());
        project.setGitBranch(dto.getGitBranch() != null ? dto.getGitBranch() : "main");
        project.setLanguage(dto.getLanguage() != null ? dto.getLanguage() : "java");
        project.setReviewStatus("PENDING");
        project.setCreatorId(userId);
        projectMapper.insert(project);

        return project;
    }

    /**
     * 上传代码包并分析
     */
    @Transactional
    public Project uploadCode(Long projectId, MultipartFile file, Long userId) throws IOException {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        // 保存上传文件
        Path uploadPath = Path.of(uploadDir, String.valueOf(projectId));
        Files.createDirectories(uploadPath);

        String fileName = file.getOriginalFilename();
        File destFile = uploadPath.resolve(fileName).toFile();
        file.transferTo(destFile);

        // 如果是zip包，解压
        Path extractPath = uploadPath;
        if (fileName != null && fileName.endsWith(".zip")) {
            extractPath = uploadPath.resolve("src");
            cn.hutool.core.util.ZipUtil.unzip(destFile, extractPath.toFile());
        }

        // 分析代码文件
        List<CodeFile> codeFiles = codeAnalysisService.analyzeProjectCode(projectId, extractPath.toString());
        long totalLines = codeFiles.stream().mapToLong(f -> f.getLineCount() != null ? f.getLineCount() : 0).sum();

        // 更新项目信息
        project.setRepoPath(extractPath.toString());
        project.setTotalFiles(codeFiles.size());
        project.setTotalLines(totalLines);
        project.setReviewStatus("PENDING");
        projectMapper.updateById(project);
        // 代码变更后旧风格画像失效，显式清空（updateById 默认不更新 null 字段），保留 styleEnabled 偏好
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, project.getId())
                .set(Project::getStyleProfile, null)
                .set(Project::getStyleAnalyzedAt, null));
        project.setStyleProfile(null);
        project.setStyleAnalyzedAt(null);

        // 上传新代码后清除旧审查数据和锁
        reviewService.resetReviewState(projectId);

        return project;
    }

    /**
     * 从Git URL拉取代码
     */
    @Transactional
    public Project pullFromGit(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            throw new BusinessException("项目未配置Git URL");
        }

        // 克隆仓库
        String repoPath = gitService.cloneRepository(
                project.getGitUrl(), project.getGitBranch(), project.getName());

        // 分析代码文件
        List<CodeFile> codeFiles = codeAnalysisService.analyzeProjectCode(projectId, repoPath);
        long totalLines = codeFiles.stream().mapToLong(f -> f.getLineCount() != null ? f.getLineCount() : 0).sum();

        // 更新项目信息
        project.setRepoPath(repoPath);
        project.setTotalFiles(codeFiles.size());
        project.setTotalLines(totalLines);
        project.setReviewStatus("PENDING");
        projectMapper.updateById(project);
        // 代码变更后旧风格画像失效，显式清空（updateById 默认不更新 null 字段），保留 styleEnabled 偏好
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, project.getId())
                .set(Project::getStyleProfile, null)
                .set(Project::getStyleAnalyzedAt, null));
        project.setStyleProfile(null);
        project.setStyleAnalyzedAt(null);

        // 拉取新代码后清除旧审查数据和锁
        reviewService.resetReviewState(projectId);

        return project;
    }

    /**
     * 获取用户的项目列表
     */
    public Page<ProjectVO> getUserProjects(Long userId, int page, int size) {
        Page<Project> projectPage = new Page<>(page, size);
        List<Project> projects = projectMapper.findProjectsByUserId(userId);

        // 手动分页（简化版）
        int start = (page - 1) * size;
        int end = Math.min(start + size, projects.size());
        List<Project> pageList = projects.subList(
                Math.min(start, projects.size()), end);

        Page<ProjectVO> resultPage = new Page<>(page, size);
        resultPage.setTotal(projects.size());
        resultPage.setRecords(pageList.stream().map(this::toVO).collect(Collectors.toList()));
        return resultPage;
    }

    /**
     * 获取项目详情
     */
    public ProjectVO getProjectDetail(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return toVO(project);
    }

    /**
     * 删除项目（级联删除关联数据）
     */
    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!project.getCreatorId().equals(userId)) {
            throw new BusinessException("只有项目创建者可以删除");
        }

        // 级联删除相关数据
        List<CodeFile> files = codeFileMapper.findByProjectId(projectId);
        for (CodeFile file : files) {
            codeChunkMapper.delete(new LambdaQueryWrapper<CodeChunk>()
                    .eq(CodeChunk::getFileId, file.getId()));
            codeFileMapper.deleteById(file.getId());
        }

        // 删除审查数据
        reviewIssueMapper.delete(new LambdaQueryWrapper<ReviewIssue>()
                .eq(ReviewIssue::getProjectId, projectId));
        reviewTaskMapper.delete(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getProjectId, projectId));
        reviewReportMapper.delete(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getProjectId, projectId));

        // 删除项目（MyBatis-Plus @TableLogic 逻辑删除）
        projectMapper.deleteById(projectId);
        log.info("项目[{}]已删除", projectId);
    }

    /**
     * 删除单个文件
     */
    @Transactional
    public void deleteFile(Long projectId, Long fileId, Long userId) {
        CodeFile file = codeFileMapper.selectById(fileId);
        if (file == null || !file.getProjectId().equals(projectId)) {
            throw new BusinessException("文件不存在");
        }

        // 删除文件关联的切片
        codeChunkMapper.delete(new LambdaQueryWrapper<CodeChunk>()
                .eq(CodeChunk::getFileId, fileId));
        codeFileMapper.deleteById(fileId);

        // 更新项目文件计数
        Project project = projectMapper.selectById(projectId);
        long remainingFiles = codeFileMapper.countByProjectId(projectId);
        project.setTotalFiles((int) remainingFiles);
        projectMapper.updateById(project);

        // 如果删除文件后没有文件了，重置审查状态
        if (remainingFiles == 0) {
            reviewService.resetReviewState(projectId);
        }

        log.info("文件[{}]已从项目[{}]删除", fileId, projectId);
    }

    private ProjectVO toVO(Project project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setTeamId(project.getTeamId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setSourceType(project.getSourceType());
        vo.setGitUrl(project.getGitUrl());
        vo.setGitBranch(project.getGitBranch());
        vo.setLanguage(project.getLanguage());
        vo.setTotalFiles(project.getTotalFiles());
        vo.setTotalLines(project.getTotalLines());
        vo.setReviewStatus(project.getReviewStatus());
        vo.setStyleEnabled(project.getStyleEnabled());
        vo.setStyleAnalyzed(project.getStyleProfile() != null && !project.getStyleProfile().isBlank());
        vo.setCreatorId(project.getCreatorId());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());

        // 统计问题数
        List<ReviewIssue> issues = reviewIssueMapper.findByProjectId(project.getId());
        vo.setIssueCount(issues.size());
        vo.setCriticalCount((int) issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count());
        vo.setMajorCount((int) issues.stream().filter(i -> "MAJOR".equals(i.getSeverity())).count());
        vo.setMinorCount((int) issues.stream().filter(i -> "MINOR".equals(i.getSeverity())).count());
        vo.setInfoCount((int) issues.stream().filter(i -> "INFO".equals(i.getSeverity())).count());

        return vo;
    }
}
