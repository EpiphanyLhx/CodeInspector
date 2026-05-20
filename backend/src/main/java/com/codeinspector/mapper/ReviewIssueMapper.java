package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.ReviewIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewIssueMapper extends BaseMapper<ReviewIssue> {
    @Select("SELECT * FROM review_issue WHERE project_id = #{projectId} ORDER BY severity, create_time DESC")
    List<ReviewIssue> findByProjectId(Long projectId);

    @Select("SELECT severity, COUNT(*) as cnt FROM review_issue WHERE project_id = #{projectId} GROUP BY severity")
    List<Map<String, Object>> countBySeverity(Long projectId);

    @Select("SELECT category, COUNT(*) as cnt FROM review_issue WHERE project_id = #{projectId} GROUP BY category")
    List<Map<String, Object>> countByCategory(Long projectId);

    @Select("SELECT * FROM review_issue WHERE project_id = #{projectId} AND file_path = #{filePath} ORDER BY line_start")
    List<ReviewIssue> findByFile(@Param("projectId") Long projectId, @Param("filePath") String filePath);
}
