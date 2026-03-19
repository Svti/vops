package com.vti.vops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vti.vops.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
