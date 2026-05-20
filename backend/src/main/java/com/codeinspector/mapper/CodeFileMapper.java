package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.CodeFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CodeFileMapper extends BaseMapper<CodeFile> {
    @Select("SELECT * FROM code_file WHERE project_id = #{projectId}")
    List<CodeFile> findByProjectId(Long projectId);

    @Select("SELECT COUNT(*) FROM code_file WHERE project_id = #{projectId}")
    Long countByProjectId(Long projectId);
}
