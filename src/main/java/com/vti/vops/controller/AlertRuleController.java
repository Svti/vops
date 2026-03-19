package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vti.vops.entity.AlertNotifier;
import com.vti.vops.entity.AlertRule;
import com.vti.vops.entity.AlertRuleNotifier;
import com.vti.vops.mapper.AlertNotifierMapper;
import com.vti.vops.mapper.AlertRuleNotifierMapper;
import com.vti.vops.service.IAlertRuleService;
import com.vti.vops.service.IHostAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警规则：列表、新增/编辑、删除
 */
@Controller
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertRuleController {

    private final IAlertRuleService alertRuleService;
    private final IHostAlertRuleService hostAlertRuleService;
    private final AlertNotifierMapper alertNotifierMapper;
    private final AlertRuleNotifierMapper alertRuleNotifierMapper;

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<AlertRule> allRules = alertRuleService.list();
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            allRules = allRules.stream()
                    .filter(r -> (r.getName() != null && r.getName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        model.addAttribute("rules", allRules);
        model.addAttribute("q", q != null ? q : "");
        return "alert/list";
    }

    /** 弹窗用表单片段（新增不传 id，编辑传 id）。规则仅定义条件，不在此处选主机，主机在「主机管理」中配置；通知渠道在此勾选。 */
    @GetMapping("/form")
    public String formFragment(@RequestParam(required = false) Long id, Model model) {
        List<AlertNotifier> notifiers = alertNotifierMapper.selectList(new LambdaQueryWrapper<AlertNotifier>().orderByAsc(AlertNotifier::getId));
        model.addAttribute("notifiers", notifiers);
        List<Long> ruleNotifierIds = new ArrayList<>();
        if (id != null) {
            AlertRule rule = alertRuleService.getById(id);
            if (rule != null) {
                ruleNotifierIds = alertRuleNotifierMapper.selectList(
                        new LambdaQueryWrapper<AlertRuleNotifier>().eq(AlertRuleNotifier::getRuleId, id))
                        .stream().map(AlertRuleNotifier::getNotifierId).filter(nid -> nid != null).distinct().toList();
                model.addAttribute("rule", rule);
                model.addAttribute("ruleNotifierIds", ruleNotifierIds);
                model.addAttribute("fragment", true);
                return "alert/edit";
            }
        }
        model.addAttribute("rule", new AlertRule());
        model.addAttribute("ruleNotifierIds", ruleNotifierIds);
        model.addAttribute("fragment", true);
        return "alert/edit";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        List<AlertNotifier> notifiers = alertNotifierMapper.selectList(new LambdaQueryWrapper<AlertNotifier>().orderByAsc(AlertNotifier::getId));
        model.addAttribute("notifiers", notifiers);
        List<Long> ruleNotifierIds = new ArrayList<>();
        if (id != null) {
            AlertRule rule = alertRuleService.getById(id);
            if (rule != null) {
                ruleNotifierIds = alertRuleNotifierMapper.selectList(
                        new LambdaQueryWrapper<AlertRuleNotifier>().eq(AlertRuleNotifier::getRuleId, id))
                        .stream().map(AlertRuleNotifier::getNotifierId).filter(nid -> nid != null).distinct().toList();
                model.addAttribute("rule", rule);
                model.addAttribute("ruleNotifierIds", ruleNotifierIds);
                return "alert/edit";
            }
        }
        model.addAttribute("rule", new AlertRule());
        model.addAttribute("ruleNotifierIds", ruleNotifierIds);
        return "alert/edit";
    }

    @PostMapping("/save")
    public Object save(@ModelAttribute AlertRule rule, @RequestParam(required = false) String enabled,
                       @RequestParam(required = false) List<Long> notifierIds,
                       RedirectAttributes ra, HttpServletRequest request) {
        rule.setEnabled("1".equals(enabled) ? 1 : 0);
        if (rule.getSeverity() == null) rule.setSeverity(1);
        if (rule.getDurationSeconds() == null) rule.setDurationSeconds(0);
        rule.setName(rule.getName() != null ? rule.getName().trim() : "");
        alertRuleService.saveOrUpdate(rule);
        Long ruleId = rule.getId();
        if (ruleId != null) {
            alertRuleNotifierMapper.delete(new LambdaQueryWrapper<AlertRuleNotifier>().eq(AlertRuleNotifier::getRuleId, ruleId));
            if (notifierIds != null && !notifierIds.isEmpty()) {
                for (Long notifierId : notifierIds) {
                    if (notifierId == null) continue;
                    AlertRuleNotifier rn = new AlertRuleNotifier();
                    rn.setRuleId(ruleId);
                    rn.setNotifierId(notifierId);
                    alertRuleNotifierMapper.insert(rn);
                }
            }
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        ra.addFlashAttribute("message", "保存成功");
        return "redirect:/alert";
    }

    @PostMapping("/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggle(@RequestParam Long id) {
        AlertRule rule = alertRuleService.getById(id);
        if (rule == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "规则不存在"));
        }
        int nextEnabled = (rule.getEnabled() != null && rule.getEnabled() == 1) ? 0 : 1;
        boolean updated = alertRuleService.update(new LambdaUpdateWrapper<AlertRule>()
                .eq(AlertRule::getId, id)
                .set(AlertRule::getEnabled, nextEnabled));
        if (!updated) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "更新失败"));
        }
        return ResponseEntity.ok(Map.of("success", true, "enabled", nextEnabled));
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes ra) {
        alertRuleNotifierMapper.delete(new LambdaQueryWrapper<AlertRuleNotifier>().eq(AlertRuleNotifier::getRuleId, id));
        hostAlertRuleService.deleteByRuleId(id);
        alertRuleService.removeById(id);
        ra.addFlashAttribute("message", "已删除");
        return "redirect:/alert";
    }
}
