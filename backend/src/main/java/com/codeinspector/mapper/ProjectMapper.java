package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("SELECT p.* FROM project p " +
            "LEFT JOIN team_member tm ON p.team_id = tm.team_id AND tm.user_id = #{userId} " +
            "WHERE p.deleted = 0 AND (tm.user_id IS NOT NULL OR p.creator_id = #{userId}) " +
            "ORDER BY p.create_time DESC")
    List<Project> findProjectsByUserId(Long userId);
}
