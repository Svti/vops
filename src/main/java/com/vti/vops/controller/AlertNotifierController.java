package com.vti.vops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.alert.AlertNotifierDispatcher;
import com.vti.vops.entity.AlertNotifier;
import com.vti.vops.mapper.AlertNotifierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警通知渠道：列表、新增/编辑、删除
 */
@Controller
@RequestMapping("/notifier")
@RequiredArgsConstructor
public class AlertNotifierController {

    private final AlertNotifierMapper alertNotifierMapper;
    private final AlertNotifierDispatcher alertNotifierDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<AlertNotifier> list = alertNotifierMapper.selectList(null);
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            list = list.stream()
                    .filter(n -> n.getName() != null && n.getName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }
        model.addAttribute("notifiers", list);
        model.addAttribute("q", q != null ? q : "");
        return "notifier/list";
    }

    /** 弹窗用表单片段（新增不传 id，编辑传 id） */
    @GetMapping("/form")
    public String formFragment(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            AlertNotifier n = alertNotifierMapper.selectById(id);
            if (n != null) {
                model.addAttribute("notifier", n);
                model.addAttribute("configWebhook", getConfigValue(n.getConfigJson(), "webhook"));
                model.addAttribute("configSecret", getConfigValue(n.getConfigJson(), "secret"));
                model.addAttribute("configTo", getConfigValue(n.getConfigJson(), "to"));
                model.addAttribute("fragment", true);
                return "notifier/edit";
            }
        }
        model.addAttribute("notifier", new AlertNotifier());
        model.addAttribute("configWebhook", "");
        model.addAttribute("configSecret", "");
        model.addAttribute("configTo", "");
        model.addAttribute("fragment", true);
        return "notifier/edit";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            AlertNotifier n = alertNotifierMapper.selectById(id);
            if (n != null) {
                model.addAttribute("notifier", n);
                model.addAttribute("configWebhook", getConfigValue(n.getConfigJson(), "webhook"));
                model.addAttribute("configSecret", getConfigValue(n.getConfigJson(), "secret"));
                model.addAttribute("configTo", getConfigValue(n.getConfigJson(), "to"));
                return "notifier/edit";
            }
        }
        model.addAttribute("notifier", new AlertNotifier());
        model.addAttribute("configWebhook", "");
        model.addAttribute("configSecret", "");
        model.addAttribute("configTo", "");
        return "notifier/edit";
    }

    @PostMapping("/save")
    public Object save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String channel,
            @RequestParam(required = false) String webhook,
            @RequestParam(required = false) String secret,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String enabled,
            RedirectAttributes ra,
            HttpServletRequest request) {
        AlertNotifier n = id != null ? alertNotifierMapper.selectById(id) : new AlertNotifier();
        if (n == null) n = new AlertNotifier();
        n.setName(name);
        n.setChannel(channel);
        n.setEnabled("1".equals(enabled) ? 1 : 0);
        Map<String, String> config = new HashMap<>();
        if ("dingtalk".equalsIgnoreCase(channel) || "feishu".equalsIgnoreCase(channel) || "wecom".equalsIgnoreCase(channel)) {
            if (webhook != null && !webhook.isBlank()) config.put("webhook", webhook.trim());
            if ("dingtalk".equalsIgnoreCase(channel) && secret != null && !secret.isBlank()) config.put("secret", secret.trim());
        } else if ("email".equalsIgnoreCase(channel)) {
            if (to != null && !to.isBlank()) config.put("to", to.trim());
        }
        try {
            n.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            n.setConfigJson("{}");
        }
        if (n.getId() != null) {
            alertNotifierMapper.updateById(n);
        } else {
            alertNotifierMapper.insert(n);
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        ra.addFlashAttribute("message", "保存成功");
        return "redirect:/notifier";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes ra) {
        alertNotifierMapper.deleteById(id);
        ra.addFlashAttribute("message", "已删除");
        return "redirect:/notifier";
    }

    /** 测试发送：按 id 使用已保存配置，或按 channel+webhook/to 使用当前表单配置（保存前可测） */
    @PostMapping("/test-send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testSend(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String webhook,
            @RequestParam(required = false) String secret,
            @RequestParam(required = false) String to) {
        AlertNotifier notifier;
        if (id != null) {
            notifier = alertNotifierMapper.selectById(id);
            if (notifier == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "渠道不存在"));
            }
        } else {
            if (channel == null || channel.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请选择渠道类型"));
            }
            String ch = channel.trim().toLowerCase();
            Map<String, String> config = new HashMap<>();
            if ("dingtalk".equals(ch) || "feishu".equals(ch) || "wecom".equals(ch)) {
                if (webhook == null || webhook.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请填写 Webhook URL"));
                }
                config.put("webhook", webhook.trim());
                if ("dingtalk".equals(ch) && secret != null && !secret.isBlank()) config.put("secret", secret.trim());
            } else if ("email".equals(ch)) {
                if (to == null || to.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请填写收件人"));
                }
                config.put("to", to.trim());
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "不支持的渠道类型"));
            }
            notifier = new AlertNotifier();
            notifier.setChannel(ch);
            try {
                notifier.setConfigJson(objectMapper.writeValueAsString(config));
            } catch (Exception e) {
                notifier.setConfigJson("{}");
            }
        }
        Map<String, Object> result = alertNotifierDispatcher.sendTest(notifier);
        return ResponseEntity.ok(result);
    }

    private String getConfigValue(String configJson, String key) {
        if (configJson == null || configJson.isBlank()) return "";
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = objectMapper.readValue(configJson, Map.class);
            return m.getOrDefault(key, "");
        } catch (Exception e) {
            return "";
        }
    }
}
