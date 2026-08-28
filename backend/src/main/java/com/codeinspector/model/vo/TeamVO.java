package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeamVO {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private LocalDateTime createTime;
    /** 当前登录用户在该团队中的角色 */
    private String myRole;
    /** 团队成员数量 */
    private Integer memberCount;
}
