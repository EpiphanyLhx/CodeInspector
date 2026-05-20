package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("SELECT p.* FROM project p INNER JOIN team_member tm ON p.team_id = tm.team_id " +
            "WHERE tm.user_id = #{userId} AND p.deleted = 0 ORDER BY p.create_time DESC")
    List<Project> findProjectsByUserId(Long userId);
}
