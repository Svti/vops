package com.vti.vops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vti.vops.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
