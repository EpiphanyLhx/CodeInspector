package com.codeinspector.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.model.entity.User;
import com.codeinspector.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化 - 启动时创建默认管理员账户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 检查admin是否存在，不存在则创建，存在则更新密码（确保密码正确）
        User existAdmin = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (existAdmin == null) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@codeinspector.com");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("默认管理员账户已创建: admin / admin123");
        } else {
            // 更新密码确保正确（schema.sql里的哈希是假数据）
            existAdmin.setPassword(passwordEncoder.encode("admin123"));
            userMapper.updateById(existAdmin);
            log.info("管理员密码已重置: admin / admin123");
        }
    }
}
