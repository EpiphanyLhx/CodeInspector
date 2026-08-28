package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.entity.Team;
import com.codeinspector.model.vo.TeamMemberVO;
import com.codeinspector.model.vo.TeamProjectVO;
import com.codeinspector.model.vo.TeamReviewVO;
import com.codeinspector.model.vo.TeamVO;
import com.codeinspector.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.codeinspector.model.entity.User;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    // ==================== 团队 CRUD ====================

    @PostMapping
    public Result<Team> create(@RequestBody Map<String, String> body,
                                @AuthenticationPrincipal User user) {
        Team team = teamService.createTeam(
                body.get("name"), body.get("description"), user.getId());
        return Result.success(team);
    }

    @GetMapping("/my")
    public Result<List<TeamVO>> myTeams(@AuthenticationPrincipal User user) {
        return Result.success(teamService.getUserTeams(user.getId()));
    }

    @DeleteMapping("/{teamId}")
    public Result<Void> deleteTeam(@PathVariable Long teamId,
                                    @AuthenticationPrincipal User user) {
        teamService.deleteTeam(teamId, user.getId());
        return Result.success();
    }

    // ==================== 邀请码 ====================

    /** 查看邀请码（LEADER/ADMIN） */
    @GetMapping("/{teamId}/invite-code")
    public Result<String> getInviteCode(@PathVariable Long teamId,
                                         @AuthenticationPrincipal User user) {
        return Result.success(teamService.getInviteCode(teamId, user.getId()));
    }

    /** 生成邀请码（LEADER/ADMIN，已有则返回已有） */
    @PostMapping("/{teamId}/invite-code/generate")
    public Result<String> generateInviteCode(@PathVariable Long teamId,
                                              @AuthenticationPrincipal User user) {
        return Result.success(teamService.generateInviteCode(teamId, user.getId()));
    }

    /** 重新生成邀请码（LEADER/ADMIN，始终生成新码） */
    @PostMapping("/{teamId}/invite-code/regenerate")
    public Result<String> regenerateInviteCode(@PathVariable Long teamId,
                                                @AuthenticationPrincipal User user) {
        return Result.success(teamService.regenerateInviteCode(teamId, user.getId()));
    }

    /** 通过邀请码加入团队（任意已登录用户） */
    @PostMapping("/join")
    public Result<Team> joinByInviteCode(@RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal User user) {
        Team team = teamService.joinByInviteCode(body.get("inviteCode"), user.getId());
        return Result.success("加入团队成功", team);
    }

    // ==================== 成员管理 ====================

    @GetMapping("/{teamId}/members")
    public Result<List<TeamMemberVO>> members(@PathVariable Long teamId,
                                               @AuthenticationPrincipal User user) {
        return Result.success(teamService.getTeamMembers(teamId, user.getId()));
    }

    @PostMapping("/{teamId}/members")
    public Result<Void> addMember(@PathVariable Long teamId,
                                   @RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal User user) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String role = (String) body.get("role");
        teamService.addMember(teamId, userId, role, user.getId());
        return Result.success();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long teamId,
                                      @PathVariable Long userId,
                                      @AuthenticationPrincipal User user) {
        teamService.removeMember(teamId, userId, user.getId());
        return Result.success();
    }

    /** 修改成员角色（仅 LEADER） */
    @PutMapping("/{teamId}/members/{userId}/role")
    public Result<Void> updateMemberRole(@PathVariable Long teamId,
                                          @PathVariable Long userId,
                                          @RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal User user) {
        teamService.updateMemberRole(teamId, userId, body.get("role"), user.getId());
        return Result.success();
    }

    // ==================== 团队项目与审查记录 ====================

    /** 团队下所有项目（所有成员可见） */
    @GetMapping("/{teamId}/projects")
    public Result<List<TeamProjectVO>> teamProjects(@PathVariable Long teamId,
                                                     @AuthenticationPrincipal User user) {
        return Result.success(teamService.getTeamProjects(teamId, user.getId()));
    }

    /** 某成员在团队内创建的项目（所有成员可见） */
    @GetMapping("/{teamId}/members/{userId}/projects")
    public Result<List<TeamProjectVO>> memberProjects(@PathVariable Long teamId,
                                                       @PathVariable Long userId,
                                                       @AuthenticationPrincipal User user) {
        return Result.success(teamService.getMemberProjects(teamId, userId, user.getId()));
    }

    /** 某成员在团队内提交的审查记录（所有成员可见） */
    @GetMapping("/{teamId}/members/{userId}/reviews")
    public Result<List<TeamReviewVO>> memberReviews(@PathVariable Long teamId,
                                                     @PathVariable Long userId,
                                                     @AuthenticationPrincipal User user) {
        return Result.success(teamService.getMemberReviews(teamId, userId, user.getId()));
    }
}
