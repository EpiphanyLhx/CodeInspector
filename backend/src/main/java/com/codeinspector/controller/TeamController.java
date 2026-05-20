package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.entity.Team;
import com.codeinspector.model.entity.TeamMember;
import com.codeinspector.model.entity.User;
import com.codeinspector.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public Result<Team> create(@RequestBody Map<String, String> body,
                                @AuthenticationPrincipal User user) {
        Team team = teamService.createTeam(
                body.get("name"), body.get("description"), user.getId());
        return Result.success(team);
    }

    @GetMapping("/my")
    public Result<List<Team>> myTeams(@AuthenticationPrincipal User user) {
        return Result.success(teamService.getUserTeams(user.getId()));
    }

    @GetMapping("/{teamId}/members")
    public Result<List<TeamMember>> members(@PathVariable Long teamId) {
        return Result.success(teamService.getTeamMembers(teamId));
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

    @DeleteMapping("/{teamId}")
    public Result<Void> deleteTeam(@PathVariable Long teamId,
                                    @AuthenticationPrincipal User user) {
        teamService.deleteTeam(teamId, user.getId());
        return Result.success();
    }
}
