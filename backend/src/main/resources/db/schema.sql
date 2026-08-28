-- CodeInspector 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS code_inspector DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE code_inspector;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT '密码(BCrypt)',
    `email` VARCHAR(128) COMMENT '邮箱',
    `avatar` VARCHAR(512) COMMENT '头像URL',
    `role` VARCHAR(32) NOT NULL DEFAULT 'DEVELOPER' COMMENT '角色: ADMIN/TEAM_LEADER/DEVELOPER/VIEWER',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    INDEX `idx_username` (`username`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 团队表
CREATE TABLE IF NOT EXISTS `team` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '团队名称',
    `description` VARCHAR(512) COMMENT '团队描述',
    `owner_id` BIGINT NOT NULL COMMENT '创建者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队表';

-- 团队成员表
CREATE TABLE IF NOT EXISTS `team_member` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_id` BIGINT NOT NULL COMMENT '团队ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role` VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '团队角色: LEADER/ADMIN/MEMBER',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_team_user` (`team_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队成员表';

-- 项目表
CREATE TABLE IF NOT EXISTS `project` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_id` BIGINT NOT NULL COMMENT '所属团队ID',
    `name` VARCHAR(256) NOT NULL COMMENT '项目名称',
    `description` VARCHAR(1024) COMMENT '项目描述',
    `source_type` VARCHAR(32) NOT NULL COMMENT '来源: UPLOAD/GIT',
    `git_url` VARCHAR(1024) COMMENT 'Git仓库URL',
    `git_branch` VARCHAR(128) DEFAULT 'main' COMMENT 'Git分支',
    `repo_path` VARCHAR(512) COMMENT '本地仓库路径',
    `language` VARCHAR(64) DEFAULT 'java' COMMENT '主要语言',
    `total_files` INT DEFAULT 0 COMMENT '文件总数',
    `total_lines` BIGINT DEFAULT 0 COMMENT '代码总行数',
    `review_status` VARCHAR(32) DEFAULT 'PENDING' COMMENT '审查状态: PENDING/IN_PROGRESS/COMPLETED/FAILED',
    `style_profile` TEXT COMMENT '代码风格画像(自动分析生成)',
    `style_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用按用户代码风格审查: 1是 0否',
    `style_analyzed_at` DATETIME COMMENT '风格画像最后分析时间',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    INDEX `idx_team` (`team_id`),
    INDEX `idx_creator` (`creator_id`),
    INDEX `idx_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 代码文件表
CREATE TABLE IF NOT EXISTS `code_file` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `file_path` VARCHAR(1024) NOT NULL COMMENT '文件相对路径',
    `file_name` VARCHAR(256) NOT NULL COMMENT '文件名',
    `file_content` LONGTEXT COMMENT '文件内容',
    `ast_data` LONGTEXT COMMENT 'AST解析数据(JSON)',
    `line_count` INT DEFAULT 0 COMMENT '行数',
    `chunk_count` INT DEFAULT 0 COMMENT '切片数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码文件表';

-- 代码切片表 (用于Token限制)
CREATE TABLE IF NOT EXISTS `code_chunk` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `file_id` BIGINT NOT NULL COMMENT '文件ID',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `chunk_index` INT NOT NULL COMMENT '切片序号',
    `chunk_type` VARCHAR(32) COMMENT '切片类型: CLASS/METHOD/BLOCK',
    `element_name` VARCHAR(256) COMMENT '元素名称',
    `chunk_content` LONGTEXT NOT NULL COMMENT '切片内容',
    `start_line` INT COMMENT '起始行',
    `end_line` INT COMMENT '结束行',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_file` (`file_id`),
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码切片表';

-- 审查任务表
CREATE TABLE IF NOT EXISTS `review_task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `chunk_id` BIGINT COMMENT '切片ID',
    `user_id` BIGINT NOT NULL COMMENT '提交者ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/QUEUED/PROCESSING/COMPLETED/FAILED',
    `error_msg` VARCHAR(1024) COMMENT '错误信息',
    `ai_provider` VARCHAR(32) COMMENT 'AI提供商',
    `ai_model` VARCHAR(64) COMMENT 'AI模型',
    `prompt_tokens` INT DEFAULT 0 COMMENT 'Prompt Token数',
    `completion_tokens` INT DEFAULT 0 COMMENT '回复Token数',
    `start_time` DATETIME COMMENT '开始时间',
    `finish_time` DATETIME COMMENT '完成时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_project` (`project_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查任务表';

-- 审查问题表
CREATE TABLE IF NOT EXISTS `review_issue` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `file_path` VARCHAR(1024) NOT NULL COMMENT '文件路径',
    `line_start` INT NOT NULL COMMENT '起始行',
    `line_end` INT COMMENT '结束行',
    `severity` VARCHAR(32) NOT NULL COMMENT '严重程度: CRITICAL/MAJOR/MINOR/INFO',
    `category` VARCHAR(64) NOT NULL COMMENT '问题分类: SECURITY/BUG/CODE_STYLE/PERFORMANCE/BEST_PRACTICE',
    `title` VARCHAR(512) NOT NULL COMMENT '问题标题',
    `description` TEXT COMMENT '问题描述',
    `suggestion` TEXT COMMENT '修复建议',
    `code_snippet` TEXT COMMENT '问题代码片段',
    `fixed_code` TEXT COMMENT '修复后代码',
    `status` VARCHAR(32) DEFAULT 'OPEN' COMMENT '状态: OPEN/RESOLVED/IGNORED',
    `resolved_by` BIGINT COMMENT '解决人ID',
    `resolved_time` DATETIME COMMENT '解决时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_task` (`task_id`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_severity` (`severity`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查问题表';

-- 审查报告表
CREATE TABLE IF NOT EXISTS `review_report` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT NOT NULL UNIQUE COMMENT '项目ID',
    `total_issues` INT DEFAULT 0 COMMENT '问题总数',
    `critical_count` INT DEFAULT 0 COMMENT '严重问题数',
    `major_count` INT DEFAULT 0 COMMENT '重要问题数',
    `minor_count` INT DEFAULT 0 COMMENT '次要问题数',
    `info_count` INT DEFAULT 0 COMMENT '提示数',
    `security_count` INT DEFAULT 0 COMMENT '安全问题数',
    `bug_count` INT DEFAULT 0 COMMENT 'Bug数',
    `style_count` INT DEFAULT 0 COMMENT '代码风格问题数',
    `performance_count` INT DEFAULT 0 COMMENT '性能问题数',
    `best_practice_count` INT DEFAULT 0 COMMENT '最佳实践问题数',
    `bug_rate` DECIMAL(10,4) COMMENT 'Bug率',
    `reviewed_files` INT DEFAULT 0 COMMENT '已审查文件数',
    `reviewed_lines` BIGINT DEFAULT 0 COMMENT '已审查行数',
    `summary` TEXT COMMENT '审查总结',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查报告表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT COMMENT '用户ID',
    `username` VARCHAR(64) COMMENT '用户名',
    `module` VARCHAR(64) COMMENT '操作模块',
    `action` VARCHAR(64) COMMENT '操作动作',
    `target` VARCHAR(256) COMMENT '操作目标',
    `ip` VARCHAR(64) COMMENT 'IP地址',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 用户API密钥配置表
CREATE TABLE IF NOT EXISTS `user_api_key` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `provider` VARCHAR(32) NOT NULL COMMENT 'AI提供商: tongyi/wenxin/openai/custom',
    `api_key_encrypted` TEXT COMMENT 'API Key(AES加密)',
    `secret_key_encrypted` TEXT COMMENT 'Secret Key(AES加密, 文心一言等需要)',
    `base_url` VARCHAR(512) COMMENT 'API端点URL',
    `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
    `is_active` TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前激活: 1是 0否',
    `is_valid` TINYINT NOT NULL DEFAULT 1 COMMENT '是否已验证: 1已验证 0未验证',
    `last_validated_at` DATETIME COMMENT '最后验证时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_user_active` (`user_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户API密钥配置表';

-- 兼容已存在的数据库: 为 project 表补充代码风格相关字段(已存在则忽略报错)
ALTER TABLE `project` ADD COLUMN `style_profile` TEXT COMMENT '代码风格画像(自动分析生成)' AFTER `review_status`;
ALTER TABLE `project` ADD COLUMN `style_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用按用户代码风格审查: 1是 0否' AFTER `style_profile`;
ALTER TABLE `project` ADD COLUMN `style_analyzed_at` DATETIME COMMENT '风格画像最后分析时间' AFTER `style_enabled`;

-- 插入默认管理员 (密码: admin123, BCrypt加密)
INSERT INTO `user` (`username`, `password`, `email`, `role`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EHs', 'admin@codeinspector.com', 'ADMIN')
ON DUPLICATE KEY UPDATE `username` = `username`;
