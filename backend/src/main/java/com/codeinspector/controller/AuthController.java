package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.dto.LoginDTO;
import com.codeinspector.model.dto.PasswordChangeDTO;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.LoginVO;
import com.codeinspector.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return Result.success();
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestParam String username,
                                  @RequestParam String password,
                                  @RequestParam(required = false) String email) {
        authService.register(username, password, email);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<User> currentUser(@AuthenticationPrincipal User user) {
        return Result.success(authService.getCurrentUser(user.getId()));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@AuthenticationPrincipal User user,
                                       @RequestBody Map<String, String> body) {
        return Result.success(authService.updateProfile(user.getId(), body));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody PasswordChangeDTO dto) {
        authService.changePassword(user.getId(), dto);
        return Result.success();
    }

    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@AuthenticationPrincipal User user,
                                                     @RequestParam("file") MultipartFile file) {
        String url = authService.uploadAvatar(user.getId(), file);
        return Result.success(Map.of("url", url));
    }
}
