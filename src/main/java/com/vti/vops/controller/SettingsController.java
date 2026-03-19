package com.vti.vops.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 系统设置（参考 VTI OPS 布局：SSH 密钥、告警规则、通知渠道、用户权限等入口）
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    @GetMapping
    public String index() {
        return "settings/index";
    }
}
