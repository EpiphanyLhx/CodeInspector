package com.codeinspector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeinspector.model.entity.UserApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户API Key Mapper
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {

    /**
     * 查找用户当前激活的API Key
     */
    @Select("SELECT * FROM user_api_key WHERE user_id = #{userId} AND is_active = 1 LIMIT 1")
    UserApiKey findActiveByUserId(@Param("userId") Long userId);
}
