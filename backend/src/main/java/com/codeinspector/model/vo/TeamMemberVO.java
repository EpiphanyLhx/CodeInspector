package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeamMemberVO {
    private Long id;
    private Long teamId;
    private Long userId;
    private String role;
    private LocalDateTime joinTime;
    // 关联的用户信息
    private String username;
    private String avatar;
    private String email;
}
