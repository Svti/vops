package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.SshKey;
import com.vti.vops.service.IHostService;
import com.vti.vops.service.ISshKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SSH 私钥库：统一管理私钥，添加主机时可选择
 */
@Controller
@RequestMapping("/sshkey")
@RequiredArgsConstructor
public class SshKeyController {

    private final ISshKeyService sshKeyService;
    private final IHostService hostService;

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<SshKey> keys = sshKeyService.listNames();
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            keys = keys.stream()
                    .filter(k -> (k.getName() != null && k.getName().toLowerCase().contains(kw))
                            || (k.getDescription() != null && k.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        List<Long> keyIds = keys.stream().map(SshKey::getId).filter(id -> id != null).toList();
        Map<Long, List<Host>> byId = keyIds.isEmpty() ? Map.of() : hostService.listHostsGroupedBySshKeyIds(keyIds);
        Map<String, List<Host>> hostsByKeyId = new HashMap<>();
        byId.forEach((id, list) -> hostsByKeyId.put(String.valueOf(id), list));
        model.addAttribute("keys", keys);
        model.addAttribute("hostsByKeyId", hostsByKeyId);
        model.addAttribute("q", q != null ? q : "");
        return "sshkey/list";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            SshKey k = sshKeyService.getById(id);
            if (k != null) {
                k.setContent(null);
                model.addAttribute("key", k);
            }
        }
        if (!model.containsAttribute("key")) {
            model.addAttribute("key", new SshKey());
        }
        return "sshkey/edit";
    }

    @PostMapping("/save")
    public String save(SshKey key) {
        sshKeyService.save(key);
        return "redirect:/sshkey";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id) {
        sshKeyService.removeById(id);
        return "redirect:/sshkey";
    }

    /** 分页查询某私钥关联的主机（仅 id/name/hostname），供弹窗展示 */
    @GetMapping(value = "/hosts", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> hosts(
            @RequestParam Long keyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;
        Page<Host> p = hostService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Host>()
                        .eq(Host::getSshKeyId, keyId)
                        .eq(Host::getStatus, 1)
                        .orderByAsc(Host::getId)
                        .select(Host::getId, Host::getName, Host::getHostname));
        List<Map<String, Object>> list = p.getRecords().stream()
                .map(h -> Map.<String, Object>of("id", h.getId(), "name", h.getName() != null ? h.getName() : "", "hostname", h.getHostname() != null ? h.getHostname() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("list", list, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize()));
    }
}
