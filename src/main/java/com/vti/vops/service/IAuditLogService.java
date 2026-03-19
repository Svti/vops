package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.AuditLog;

/**
 * 审计日志服务接口
 */
public interface IAuditLogService extends IService<AuditLog> {

    void log(Long userId, String username, String action, String resourceType, String resourceId, String detail, String ip, String userAgent);
}
