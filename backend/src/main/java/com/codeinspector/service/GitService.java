package com.codeinspector.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
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

    /** 可选 HTTP 代理主机（用于访问受限网络中的仓库，如 GitHub） */
    @Value("${git.proxy.host:}")
    private String proxyHost;

    /** 可选 HTTP 代理端口 */
    @Value("${git.proxy.port:0}")
    private int proxyPort;

    @PostConstruct
    public void initProxy() {
        String host = proxyHost;
        int port = proxyPort;
        // 显式配置缺失时，回退识别标准代理环境变量
        if (host == null || host.isBlank()) {
            String env = firstNonBlank(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"),
                    System.getenv("ALL_PROXY"), System.getenv("all_proxy"),
                    System.getenv("HTTP_PROXY"), System.getenv("http_proxy"));
            if (env != null && !env.isBlank()) {
                try {
                    URI u = URI.create(env.trim());
                    host = u.getHost();
                    if (u.getPort() > 0) port = u.getPort();
                } catch (Exception ignore) {
                    // 环境变量格式不识别则忽略
                }
            }
        }
        if (host != null && !host.isBlank() && port > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
            // 仅对 JGit 生效，不影响 JVM 其它 HTTP 请求
            HttpTransport.setConnectionFactory(new ProxiedHttpConnectionFactory(proxy));
            log.info("Git HTTP 代理已配置: {}:{}", host, port);
        } else {
            log.info("Git 未配置 HTTP 代理，将直连仓库");
        }
    }

    /**
     * Git 同步结果
     * @param repoPath  本地仓库绝对路径
     * @param commitHash 同步后 HEAD 的 commit hash
     */
    public record GitSyncResult(String repoPath, String commitHash) {}

    /**
     * 克隆Git仓库（公开仓库）
     */
    public String cloneRepository(String gitUrl, String branch, String projectName) {
        return cloneRepository(gitUrl, branch, projectName, null, null);
    }

    /**
     * 克隆Git仓库（支持私有仓库凭据）
     */
    public String cloneRepository(String gitUrl, String branch, String projectName,
                                  String username, String token) {
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
            applyTransport(cloneCommand, buildCredentials(username, token));

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
     * 拉取最新代码（公开仓库）
     */
    public String pullLatest(String repoPath) {
        return pullLatest(repoPath, null, null);
    }

    /**
     * 拉取最新代码（支持私有仓库凭据）
     */
    public String pullLatest(String repoPath, String username, String token) {
        try {
            File repoDir = new File(repoPath);
            if (!repoDir.exists()) {
                throw new IllegalArgumentException("仓库路径不存在: " + repoPath);
            }

            try (Git git = Git.open(repoDir)) {
                PullCommand pull = git.pull().setTimeout(timeoutSeconds);
                applyTransport(pull, buildCredentials(username, token));
                pull.call();
                log.info("拉取最新代码成功: {}", repoPath);
                return repoPath;
            }
        } catch (Exception e) {
            log.error("Git拉取失败: {}", e.getMessage());
            throw new RuntimeException("Git拉取失败: " + e.getMessage());
        }
    }

    /**
     * 同步项目仓库到指定分支的最新代码（供团队审查任务使用）。
     * <p>同一项目复用固定本地目录 {@code cloneBaseDir/project_{projectId}}：
     * 目录已存在则 fetch + checkout + reset --hard origin/branch，不存在才 clone。
     * 不会每次 clone 到带时间戳的新目录。</p>
     *
     * @param projectId 项目ID（用于确定稳定本地目录）
     * @param gitUrl    仓库地址
     * @param branch    目标分支
     * @param username  Git用户名（可为null）
     * @param token     Git访问令牌（可为null）
     * @return 同步结果（本地路径 + 最新commit hash）
     */
    public GitSyncResult syncProjectRepository(Long projectId, String gitUrl, String branch,
                                               String username, String token) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("Git仓库URL不能为空");
        }
        String effectiveBranch = (branch == null || branch.isBlank()) ? "main" : branch.trim();
        CredentialsProvider cp = buildCredentials(username, token);

        Git git = null;
        try {
            Path basePath = Path.of(cloneBaseDir);
            Files.createDirectories(basePath);
            Path targetPath = basePath.resolve("project_" + projectId);
            File dir = targetPath.toFile();

            if (dir.exists() && new File(dir, ".git").exists()) {
                try {
                    git = Git.open(dir);
                    // 仓库地址可能被修改，同步 remote origin URL
                    StoredConfig config = git.getRepository().getConfig();
                    config.setString("remote", "origin", "url", gitUrl);
                    config.save();

                    // 抓取远端最新引用
                    FetchCommand fetch = git.fetch().setTimeout(timeoutSeconds).setRemoveDeletedRefs(true);
                    applyTransport(fetch, cp);
                    fetch.call();

                    // 检出目标分支（本地无该分支则基于 origin/branch 创建跟踪分支）
                    checkoutRemoteBranch(git, effectiveBranch);

                    // 硬重置到 origin/branch，保证工作区与远端一致（服务端仓库无人本地修改）
                    Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + effectiveBranch);
                    if (remoteRef != null) {
                        git.reset().setMode(ResetCommand.ResetType.HARD)
                                .setRef(remoteRef.getName()).call();
                    } else {
                        // 远端分支不存在则回退到 pull
                        PullCommand pull = git.pull().setTimeout(timeoutSeconds);
                        applyTransport(pull, cp);
                        pull.call();
                    }
                    log.info("项目[{}]仓库已更新到 origin/{}", projectId, effectiveBranch);
                } catch (Exception updateEx) {
                    // 本地仓库损坏或状态异常时，删除后重新克隆
                    log.warn("项目[{}]本地仓库更新失败，删除后重新克隆: {}", projectId, updateEx.getMessage());
                    if (git != null) { git.close(); git = null; }
                    deleteRecursively(dir);
                    git = cloneFresh(gitUrl, effectiveBranch, dir, cp);
                    log.info("项目[{}]仓库已重新克隆到 {}", projectId, targetPath);
                }
            } else {
                // 首次克隆
                git = cloneFresh(gitUrl, effectiveBranch, dir, cp);
                log.info("项目[{}]仓库已克隆到 {}", projectId, targetPath);
            }

            ObjectId head = git.getRepository().resolve("HEAD");
            String commitHash = head != null ? head.getName() : null;
            return new GitSyncResult(targetPath.toAbsolutePath().toString(), commitHash);
        } catch (Exception e) {
            log.error("项目[{}]Git同步失败: {}", projectId, rootMessage(e));
            throw new RuntimeException("Git代码同步失败: " + rootMessage(e), e);
        } finally {
            if (git != null) git.close();
        }
    }

    /**
     * 获取项目在本地的稳定仓库路径（不保证目录已存在）
     */
    public Path getProjectRepoPath(Long projectId) {
        return Path.of(cloneBaseDir).resolve("project_" + projectId);
    }

    /**
     * 检出远端分支：本地分支不存在则基于 origin/branch 创建并跟踪
     */
    private void checkoutRemoteBranch(Git git, String branch) throws Exception {
        boolean localExists = git.getRepository().findRef("refs/heads/" + branch) != null;
        CheckoutCommand checkout = git.checkout().setName(branch).setForce(true);
        if (!localExists) {
            Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
            if (remoteRef != null) {
                checkout.setCreateBranch(true).setStartPoint("origin/" + branch);
            }
        }
        checkout.call();
    }

    /**
     * 全新克隆到指定目录
     */
    private Git cloneFresh(String gitUrl, String branch, File dir, CredentialsProvider cp)
            throws GitAPIException {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(dir)
                .setCloneAllBranches(false)
                .setBranch(branch)
                .setTimeout(timeoutSeconds);
        applyTransport(cloneCommand, cp);
        return cloneCommand.call();
    }

    /**
     * 统一为 JGit 传输命令设置凭据（代理通过 ProxiedHttpConnectionFactory 全局作用于 JGit）
     */
    private void applyTransport(TransportCommand<?, ?> command, CredentialsProvider cp) {
        if (cp != null) {
            command.setCredentialsProvider(cp);
        }
    }

    /**
     * 递归删除目录（用于清理损坏的本地仓库）
     */
    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            log.warn("无法删除文件: {}", file.getAbsolutePath());
        }
    }

    private CredentialsProvider buildCredentials(String username, String token) {
        if (token != null && !token.isBlank()) {
            String user = (username == null || username.isBlank()) ? "oauth2" : username;
            return new UsernamePasswordCredentialsProvider(user, token);
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : e.getClass().getSimpleName();
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
