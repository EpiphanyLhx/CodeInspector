package com.codeinspector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.BusinessException;
import com.codeinspector.model.dto.LoginDTO;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.LoginVO;
import com.codeinspector.mapper.UserMapper;
import com.codeinspector.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 用户登录
     */
    public LoginVO login(LoginDTO loginDTO) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 缓存用户信息到Redis（失败不影响登录）
        try {
            String redisKey = "user:info:" + user.getId();
            redisTemplate.opsForValue().set(redisKey, user, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis缓存用户信息失败: {}", e.getMessage());
        }

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setLoginTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 退出登录 - 将Token加入黑名单
     */
    public void logout(String token) {
        long expiration = jwtTokenProvider.parseToken(token)
                .getExpiration().getTime() - System.currentTimeMillis();
        if (expiration > 0) {
            redisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + token, "1",
                    expiration, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 获取当前用户
     */
    public User getCurrentUser(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 注册用户
     */
    public void register(String username, String password, String email) {
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole("DEVELOPER");
        user.setStatus(1);
        userMapper.insert(user);
    }
}
