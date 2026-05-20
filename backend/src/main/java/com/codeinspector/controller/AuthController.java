package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.dto.LoginDTO;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.LoginVO;
import com.codeinspector.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
                                  @RequestParam String email) {
        authService.register(username, password, email);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<User> currentUser(@AuthenticationPrincipal User user) {
        return Result.success(authService.getCurrentUser(user.getId()));
    }
}
