package com.codeinspector.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Git服务 - 克隆/拉取Git仓库
 */
@Slf4j
@Service
public class GitService {

    @Value("${git.clone-base-dir:/tmp/code-inspector/repos}")
    private String cloneBaseDir;

    @Value("${git.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * 克隆Git仓库
     */
    public String cloneRepository(String gitUrl, String branch, String projectName) {
        try {
            Path basePath = Path.of(cloneBaseDir);
            Files.createDirectories(basePath);

            String repoName = extractRepoName(gitUrl);
            Path targetPath = basePath.resolve(projectName + "_" + System.currentTimeMillis());

            log.info("开始克隆仓库: {} -> {}", gitUrl, targetPath);

            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(targetPath.toFile())
                    .setCloneAllBranches(false)
                    .setTimeout(timeoutSeconds);

            if (branch != null && !branch.isEmpty()) {
                cloneCommand.setBranch(branch);
            }

            try (Git git = cloneCommand.call()) {
                log.info("仓库克隆成功: {}", gitUrl);
                return targetPath.toAbsolutePath().toString();
            }
        } catch (GitAPIException e) {
            log.error("Git克隆失败: {} - {}", gitUrl, e.getMessage());
            throw new RuntimeException("Git仓库克隆失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Git操作异常: ", e);
            throw new RuntimeException("Git操作异常: " + e.getMessage());
        }
    }

    /**
     * 拉取最新代码
     */
    public String pullLatest(String repoPath) {
        try {
            File repoDir = new File(repoPath);
            if (!repoDir.exists()) {
                throw new IllegalArgumentException("仓库路径不存在: " + repoPath);
            }

            try (Git git = Git.open(repoDir)) {
                git.pull().setTimeout(timeoutSeconds).call();
                log.info("拉取最新代码成功: {}", repoPath);
                return repoPath;
            }
        } catch (Exception e) {
            log.error("Git拉取失败: {}", e.getMessage());
            throw new RuntimeException("Git拉取失败: " + e.getMessage());
        }
    }

    private String extractRepoName(String gitUrl) {
        String name = gitUrl;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }
}
