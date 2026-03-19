package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vti.vops.entity.AuditLog;
import com.vti.vops.service.IAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 审计日志（分页）
 */
@Controller
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final IAuditLogService auditLogService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            Model model) {
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        Page<AuditLog> p = new Page<>(page, size);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getCreateTime);
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            wrapper.and(w -> w
                    .like(AuditLog::getUsername, kw)
                    .or().like(AuditLog::getAction, kw)
                    .or().like(AuditLog::getResourceType, kw)
                    .or().like(AuditLog::getResourceId, kw)
                    .or().like(AuditLog::getDetail, kw)
                    .or().like(AuditLog::getIp, kw));
        }
        auditLogService.page(p, wrapper);
        model.addAttribute("logs", p.getRecords());
        model.addAttribute("total", p.getTotal());
        model.addAttribute("currentPage", p.getCurrent());
        model.addAttribute("totalPages", p.getPages());
        model.addAttribute("pageSize", size);
        model.addAttribute("q", q != null ? q.trim() : "");
        return "audit/list";
    }
}
