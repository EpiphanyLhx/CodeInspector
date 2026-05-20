package com.codeinspector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.BusinessException;
import com.codeinspector.model.entity.Team;
import com.codeinspector.model.entity.TeamMember;
import com.codeinspector.model.entity.User;
import com.codeinspector.mapper.TeamMapper;
import com.codeinspector.mapper.TeamMemberMapper;
import com.codeinspector.mapper.UserMapper;
import com.codeinspector.mapper.ProjectMapper;
import com.codeinspector.model.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;

    /**
     * 创建团队
     */
    @Transactional
    public Team createTeam(String name, String description, Long ownerId) {
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setOwnerId(ownerId);
        teamMapper.insert(team);

        // 将创建者添加为LEADER
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(ownerId);
        member.setRole("LEADER");
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);

        return team;
    }

    /**
     * 获取用户的团队列表
     */
    public List<Team> getUserTeams(Long userId) {
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (members.isEmpty()) return List.of();

        List<Long> teamIds = members.stream().map(TeamMember::getTeamId).toList();
        return teamMapper.selectBatchIds(teamIds);
    }

    /**
     * 添加团队成员
     */
    @Transactional
    public void addMember(Long teamId, Long userId, String role, Long operatorId) {
        // 检查操作者权限
        TeamMember operator = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, operatorId));
        if (operator == null || (!"LEADER".equals(operator.getRole()) && !"ADMIN".equals(operator.getRole()))) {
            throw new BusinessException("你没有权限添加成员");
        }

        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查是否已是成员
        TeamMember existMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (existMember != null) {
            throw new BusinessException("该用户已是团队成员");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "MEMBER");
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);
    }

    /**
     * 移除团队成员
     */
    public void removeMember(Long teamId, Long userId, Long operatorId) {
        // 检查操作者权限
        TeamMember operator = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, operatorId));
        if (operator == null || (!"LEADER".equals(operator.getRole()) && !"ADMIN".equals(operator.getRole()))) {
            throw new BusinessException("你没有权限移除成员");
        }

        if (operatorId.equals(userId)) {
            throw new BusinessException("不能移除自己");
        }

        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    /**
     * 获取团队成员列表
     */
    public List<TeamMember> getTeamMembers(Long teamId) {
        return teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
    }

    /**
     * 删除团队（校验关联数据）
     */
    @Transactional
    public void deleteTeam(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("团队不存在");
        }
        if (!team.getOwnerId().equals(userId)) {
            throw new BusinessException("只有团队创建者可以删除");
        }

        // 检查是否存在项目
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId));
        if (!projects.isEmpty()) {
            throw new BusinessException("该团队下存在 " + projects.size() + " 个项目，请先删除或转移项目后再删除团队");
        }

        // 删除所有成员
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));

        // 删除团队
        teamMapper.deleteById(teamId);
        log.info("团队[{}]已被用户[{}]删除", teamId, userId);
    }
}
