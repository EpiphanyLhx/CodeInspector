package com.codeinspector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.BusinessException;
import com.codeinspector.mapper.ProjectMapper;
import com.codeinspector.mapper.ReviewTaskMapper;
import com.codeinspector.mapper.TeamMapper;
import com.codeinspector.mapper.TeamMemberMapper;
import com.codeinspector.mapper.UserMapper;
import com.codeinspector.model.entity.Project;
import com.codeinspector.model.entity.ReviewTask;
import com.codeinspector.model.entity.Team;
import com.codeinspector.model.entity.TeamMember;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.TeamMemberVO;
import com.codeinspector.model.vo.TeamProjectVO;
import com.codeinspector.model.vo.TeamReviewVO;
import com.codeinspector.model.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ReviewTaskMapper reviewTaskMapper;

    private static final Set<String> ROLE_WHITELIST = Set.of("LEADER", "ADMIN", "MEMBER");
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== 权限校验工具方法 ====================

    /**
     * 获取用户在团队中的成员记录，不存在返回 null
     */
    private TeamMember getMembership(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    /**
     * 校验调用者是团队成员，否则抛异常
     */
    private TeamMember requireTeamMember(Long teamId, Long userId) {
        TeamMember member = getMembership(teamId, userId);
        if (member == null) {
            throw new BusinessException("你不是该团队成员");
        }
        return member;
    }

    /**
     * 校验调用者是团队管理员(LEADER/ADMIN)，否则抛异常
     */
    private TeamMember requireTeamAdmin(Long teamId, Long userId) {
        TeamMember member = requireTeamMember(teamId, userId);
        if (!"LEADER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
            throw new BusinessException("你没有权限执行此操作");
        }
        return member;
    }

    /**
     * 校验调用者是团队负责人(LEADER/owner)，否则抛异常
     */
    private TeamMember requireTeamLeader(Long teamId, Long userId) {
        TeamMember member = requireTeamMember(teamId, userId);
        if (!"LEADER".equals(member.getRole())) {
            throw new BusinessException("只有团队负责人可以执行此操作");
        }
        return member;
    }

    /**
     * 校验团队存在并返回
     */
    private Team requireTeamExists(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("团队不存在");
        }
        return team;
    }

    /**
     * 校验角色值白名单
     */
    private void validateRole(String role) {
        if (role == null || !ROLE_WHITELIST.contains(role)) {
            throw new BusinessException("角色值非法，仅支持 LEADER / ADMIN / MEMBER");
        }
    }

    // ==================== 团队 CRUD ====================

    /**
     * 创建团队（创建者自动成为 LEADER）
     */
    @Transactional
    public Team createTeam(String name, String description, Long ownerId) {
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setOwnerId(ownerId);
        teamMapper.insert(team);

        // 将创建者添加为 LEADER
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(ownerId);
        member.setRole("LEADER");
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);

        return team;
    }

    /**
     * 获取当前用户加入的团队列表（携带用户在各团队中的角色）
     */
    public List<TeamVO> getUserTeams(Long userId) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();

        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        List<Team> teams = teamMapper.selectBatchIds(teamIds);

        // 各团队成员数
        Map<Long, Long> teamMemberCounts = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<TeamMember>().in(TeamMember::getTeamId, teamIds))
                .stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId, Collectors.counting()));

        // teamId -> myRole
        Map<Long, String> myRoleMap = memberships.stream()
                .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getRole));

        return teams.stream().map(team -> {
            TeamVO vo = new TeamVO();
            vo.setId(team.getId());
            vo.setName(team.getName());
            vo.setDescription(team.getDescription());
            vo.setOwnerId(team.getOwnerId());
            vo.setCreateTime(team.getCreateTime());
            vo.setMyRole(myRoleMap.get(team.getId()));
            vo.setMemberCount(teamMemberCounts.getOrDefault(team.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    /**
     * 删除团队（仅 owner，团队下有项目时拒绝）
     */
    @Transactional
    public void deleteTeam(Long teamId, Long userId) {
        Team team = requireTeamExists(teamId);
        // 所有团队接口先校验成员身份
        requireTeamMember(teamId, userId);
        if (!team.getOwnerId().equals(userId)) {
            throw new BusinessException("只有团队创建者可以删除");
        }

        // 检查是否存在项目
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId));
        if (!projects.isEmpty()) {
            throw new BusinessException("该团队下存在 " + projects.size() + " 个项目，请先删除或转移项目后再删除团队");
        }

        // 删除所有成员（TeamMember 无逻辑删除，物理删除）
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));

        // 删除团队
        teamMapper.deleteById(teamId);
        log.info("团队[{}]已被用户[{}]删除", teamId, userId);
    }

    // ==================== 成员管理 ====================

    /**
     * 获取团队成员列表（携带用户信息：用户名、头像、邮箱）
     */
    public List<TeamMemberVO> getTeamMembers(Long teamId, Long userId) {
        requireTeamExists(teamId);
        requireTeamMember(teamId, userId);

        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .orderByAsc(TeamMember::getJoinTime));

        if (members.isEmpty()) return List.of();

        // 批量查询用户信息
        List<Long> userIds = members.stream().map(TeamMember::getUserId).toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream().map(m -> {
            TeamMemberVO vo = new TeamMemberVO();
            vo.setId(m.getId());
            vo.setTeamId(m.getTeamId());
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setJoinTime(m.getJoinTime());
            User u = userMap.get(m.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setAvatar(u.getAvatar());
                vo.setEmail(u.getEmail());
            }
            return vo;
        }).toList();
    }

    /**
     * 添加团队成员（LEADER/ADMIN）
     * - ADMIN 只能添加 MEMBER
     * - LEADER 可添加 MEMBER 或 ADMIN，不能添加 LEADER
     */
    @Transactional
    public void addMember(Long teamId, Long userId, String role, Long operatorId) {
        requireTeamExists(teamId);
        TeamMember operator = requireTeamAdmin(teamId, operatorId);

        String targetRole = (role != null && !role.isBlank()) ? role : "MEMBER";
        validateRole(targetRole);

        // 不能添加 LEADER（owner 唯一）
        if ("LEADER".equals(targetRole)) {
            throw new BusinessException("不能指定负责人角色，团队负责人为创建者");
        }
        // ADMIN 只能添加 MEMBER
        if ("ADMIN".equals(operator.getRole()) && !"MEMBER".equals(targetRole)) {
            throw new BusinessException("管理员只能添加普通成员");
        }

        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查是否已是成员
        TeamMember existMember = getMembership(teamId, userId);
        if (existMember != null) {
            throw new BusinessException("该用户已是团队成员");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(targetRole);
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);
    }

    /**
     * 移除团队成员（LEADER/ADMIN）
     * - 不能移除自己
     * - 不能移除 owner(LEADER)
     */
    @Transactional
    public void removeMember(Long teamId, Long userId, Long operatorId) {
        requireTeamExists(teamId);
        requireTeamAdmin(teamId, operatorId);

        if (operatorId.equals(userId)) {
            throw new BusinessException("不能移除自己");
        }

        TeamMember target = getMembership(teamId, userId);
        if (target == null) {
            throw new BusinessException("该用户不是团队成员");
        }
        // owner(LEADER) 不可被移除
        if ("LEADER".equals(target.getRole())) {
            throw new BusinessException("团队负责人不可被移除");
        }

        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    /**
     * 修改成员角色（仅 LEADER）
     * - 不能修改 owner(LEADER) 的角色
     * - 不能修改自己的角色
     * - 新角色只能是 ADMIN 或 MEMBER
     */
    @Transactional
    public void updateMemberRole(Long teamId, Long targetUserId, String newRole, Long operatorId) {
        requireTeamExists(teamId);
        requireTeamLeader(teamId, operatorId);
        validateRole(newRole);

        if (operatorId.equals(targetUserId)) {
            throw new BusinessException("不能修改自己的角色");
        }
        // 不能将任何人设为 LEADER
        if ("LEADER".equals(newRole)) {
            throw new BusinessException("不能指定负责人角色，团队负责人为创建者");
        }

        TeamMember target = getMembership(teamId, targetUserId);
        if (target == null) {
            throw new BusinessException("该用户不是团队成员");
        }
        // owner(LEADER) 角色不可被修改
        if ("LEADER".equals(target.getRole())) {
            throw new BusinessException("团队负责人的角色不可修改");
        }

        target.setRole(newRole);
        teamMemberMapper.updateById(target);
    }

    // ==================== 邀请码 ====================

    /**
     * 生成邀请码（LEADER/ADMIN）。若已有邀请码则直接返回，否则生成新的。
     */
    @Transactional
    public String generateInviteCode(Long teamId, Long userId) {
        Team team = requireTeamExists(teamId);
        requireTeamAdmin(teamId, userId);

        if (team.getInviteCode() != null && !team.getInviteCode().isBlank()) {
            return team.getInviteCode();
        }
        String code = createUniqueInviteCode();
        team.setInviteCode(code);
        teamMapper.updateById(team);
        return code;
    }

    /**
     * 重新生成邀请码（LEADER/ADMIN），无论之前是否存在都生成新的。
     */
    @Transactional
    public String regenerateInviteCode(Long teamId, Long userId) {
        Team team = requireTeamExists(teamId);
        requireTeamAdmin(teamId, userId);

        String code = createUniqueInviteCode();
        team.setInviteCode(code);
        teamMapper.updateById(team);
        return code;
    }

    /**
     * 查看邀请码（LEADER/ADMIN）
     */
    public String getInviteCode(Long teamId, Long userId) {
        Team team = requireTeamExists(teamId);
        requireTeamAdmin(teamId, userId);
        return team.getInviteCode();
    }

    /**
     * 任意已登录用户通过邀请码加入团队，成为 MEMBER
     */
    @Transactional
    public Team joinByInviteCode(String inviteCode, Long userId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException("邀请码不能为空");
        }
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getInviteCode, inviteCode.trim()));
        if (team == null) {
            throw new BusinessException("邀请码无效或团队不存在");
        }
        if (getMembership(team.getId(), userId) != null) {
            throw new BusinessException("你已是该团队成员");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);
        log.info("用户[{}]通过邀请码加入团队[{}]", userId, team.getId());
        return team;
    }

    /**
     * 生成唯一邀请码（碰撞则重试）
     */
    private String createUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            String code = sb.toString();
            Long count = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                    .eq(Team::getInviteCode, code));
            if (count == 0) {
                return code;
            }
        }
        throw new BusinessException("邀请码生成失败，请重试");
    }

    // ==================== 团队项目与审查记录 ====================

    /**
     * 获取团队下所有项目（所有团队成员可见）
     */
    public List<TeamProjectVO> getTeamProjects(Long teamId, Long userId) {
        requireTeamExists(teamId);
        requireTeamMember(teamId, userId);

        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getTeamId, teamId)
                .orderByDesc(Project::getCreateTime));

        return toTeamProjectVOList(projects);
    }

    /**
     * 获取某个成员在团队内创建的项目（所有团队成员可见）
     */
    public List<TeamProjectVO> getMemberProjects(Long teamId, Long targetUserId, Long userId) {
        requireTeamExists(teamId);
        requireTeamMember(teamId, userId);
        if (getMembership(teamId, targetUserId) == null) {
            throw new BusinessException("该用户不是团队成员");
        }

        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getTeamId, teamId)
                .eq(Project::getCreatorId, targetUserId)
                .orderByDesc(Project::getCreateTime));

        return toTeamProjectVOList(projects);
    }

    /**
     * 获取某个成员在团队内提交的审查记录（所有团队成员可见）
     */
    public List<TeamReviewVO> getMemberReviews(Long teamId, Long targetUserId, Long userId) {
        requireTeamExists(teamId);
        requireTeamMember(teamId, userId);
        if (getMembership(teamId, targetUserId) == null) {
            throw new BusinessException("该用户不是团队成员");
        }

        // 该团队下所有项目 ID
        List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId))
                .stream().map(Project::getId).toList();
        if (projectIds.isEmpty()) return List.of();

        // 该用户在这些项目下提交的审查任务
        List<ReviewTask> tasks = reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                .in(ReviewTask::getProjectId, projectIds)
                .eq(ReviewTask::getUserId, targetUserId)
                .orderByDesc(ReviewTask::getCreateTime));

        if (tasks.isEmpty()) return List.of();

        // 批量查询项目名
        Map<Long, String> projectNameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        return tasks.stream().map(t -> {
            TeamReviewVO vo = new TeamReviewVO();
            vo.setId(t.getId());
            vo.setProjectId(t.getProjectId());
            vo.setProjectName(projectNameMap.get(t.getProjectId()));
            vo.setUserId(t.getUserId());
            vo.setStatus(t.getStatus());
            vo.setAiModel(t.getAiModel());
            vo.setCreateTime(t.getCreateTime());
            vo.setFinishTime(t.getFinishTime());
            return vo;
        }).toList();
    }

    // ==================== 私有转换方法 ====================

    private List<TeamProjectVO> toTeamProjectVOList(List<Project> projects) {
        if (projects.isEmpty()) return List.of();

        // 批量查询创建者用户名
        List<Long> creatorIds = projects.stream().map(Project::getCreatorId).distinct().toList();
        Map<Long, String> creatorNameMap = userMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        return projects.stream().map(p -> {
            TeamProjectVO vo = new TeamProjectVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setDescription(p.getDescription());
            vo.setLanguage(p.getLanguage());
            vo.setReviewStatus(p.getReviewStatus());
            vo.setCreatorId(p.getCreatorId());
            vo.setCreatorName(creatorNameMap.get(p.getCreatorId()));
            vo.setCreateTime(p.getCreateTime());
            return vo;
        }).toList();
    }
}
