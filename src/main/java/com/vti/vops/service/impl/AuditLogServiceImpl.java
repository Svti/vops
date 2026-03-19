package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.AuditLog;
import com.vti.vops.mapper.AuditLogMapper;
import com.vti.vops.service.IAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements IAuditLogService {

    @Override
    @Async
    public void log(Long userId, String username, String action, String resourceType, String resourceId, String detail, String ip, String userAgent) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        save(log);
    }
}
