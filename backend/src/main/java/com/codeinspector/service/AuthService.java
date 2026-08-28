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
import java.util.Map;
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

    /**
     * 更新用户资料
     */
    public User updateProfile(Long userId, Map<String, String> body) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        if (body.containsKey("username")) user.setUsername(body.get("username"));
        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("avatar")) user.setAvatar(body.get("avatar"));
        if (body.containsKey("role")) user.setRole(body.get("role"));

        userMapper.updateById(user);
        return user;
    }

    /**
     * 修改密码
     */
    public void changePassword(Long userId, com.codeinspector.model.dto.PasswordChangeDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 上传头像
     */
    public String uploadAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        try {
            String uploadDir = System.getProperty("java.io.tmpdir") + "/code-inspector/avatars";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String fileName = userId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            java.io.File dest = new java.io.File(dir, fileName);
            file.transferTo(dest);

            String avatarUrl = "/api/files/avatars/" + fileName;
            user.setAvatar(avatarUrl);
            userMapper.updateById(user);
            return avatarUrl;
        } catch (Exception e) {
            log.error("头像上传失败", e);
            throw new BusinessException("头像上传失败");
        }
    }
}
