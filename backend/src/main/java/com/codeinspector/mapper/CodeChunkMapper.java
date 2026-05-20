package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.CodeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CodeChunkMapper extends BaseMapper<CodeChunk> {
    @Select("SELECT * FROM code_chunk WHERE project_id = #{projectId} ORDER BY chunk_index")
    List<CodeChunk> findByProjectId(Long projectId);

    @Select("SELECT * FROM code_chunk WHERE file_id = #{fileId} ORDER BY chunk_index")
    List<CodeChunk> findByFileId(Long fileId);
}
