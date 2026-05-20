package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String email;
    private String avatar;
    private String role;
    private LocalDateTime loginTime;
}
