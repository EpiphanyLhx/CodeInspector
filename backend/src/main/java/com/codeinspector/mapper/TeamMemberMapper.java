package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
