package com.vti.vops.controller;

import com.vti.vops.config.OidcProperties;
import com.vti.vops.dto.IndexSummaryVo;
import com.vti.vops.entity.User;
import com.vti.vops.mapper.BatchScheduleMapper;
import com.vti.vops.service.IIndexSummaryService;
import com.vti.vops.service.IAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 首页、登录页、运维总览
 */
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final OidcProperties oidcProperties;
    private final IIndexSummaryService indexSummaryService;
    private final IAlertRuleService alertRuleService;
    private final BatchScheduleMapper batchScheduleMapper;

    @GetMapping("/")
    public String index(Model model) {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        model.addAttribute("user", principal);
        model.addAttribute("oidcEnabled", oidcProperties.isEnabled());

        if (principal != null && principal instanceof User) {
            User user = (User) principal;
            IndexSummaryVo summary = indexSummaryService.getSummary(user.getId());
            model.addAttribute("totalHosts", summary.getTotalHosts());
            model.addAttribute("onlineHosts", summary.getOnlineHosts());
            model.addAttribute("avgCpu", summary.getAvgCpu());
            model.addAttribute("avgMem", summary.getAvgMem());
            model.addAttribute("totalAlertRules", summary.getTotalAlertRules());
            model.addAttribute("totalBatchTasks", summary.getTotalBatchTasks());
            model.addAttribute("topHostsByCpu", summary.getTopHostsByCpu());
            model.addAttribute("topHostsByMem", summary.getTopHostsByMem());
        } else {
            model.addAttribute("totalHosts", 0);
            model.addAttribute("onlineHosts", 0);
            model.addAttribute("avgCpu", 0.0);
            model.addAttribute("avgMem", 0.0);
            model.addAttribute("totalAlertRules", alertRuleService.count());
            model.addAttribute("totalBatchTasks", batchScheduleMapper.selectCount(null));
            model.addAttribute("topHostsByCpu", Collections.emptyList());
            model.addAttribute("topHostsByMem", Collections.emptyList());
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model, HttpSession session) {
        int a = ThreadLocalRandom.current().nextInt(1, 21);
        int b = ThreadLocalRandom.current().nextInt(1, 21);
        session.setAttribute("captcha", a + b);
        model.addAttribute("captchaQuestion", a + " + " + b + " = ?");
        model.addAttribute("oidcEnabled", oidcProperties.isEnabled());
        return "login";
    }

    @GetMapping("/unauthorized")
    public String unauthorized() {
        return "unauthorized";
    }
}
