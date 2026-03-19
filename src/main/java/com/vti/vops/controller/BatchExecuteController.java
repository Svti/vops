package com.vti.vops.controller;

import com.vti.vops.entity.BatchSchedule;
import com.vti.vops.entity.BatchTask;
import com.vti.vops.entity.BatchTaskLog;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.User;
import com.vti.vops.mapper.BatchTaskMapper;
import com.vti.vops.service.IBatchExecuteService;
import com.vti.vops.service.IBatchScheduleService;
import com.vti.vops.service.IBatchTaskLogService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/batch")
@RequiredArgsConstructor
public class BatchExecuteController {

    private final IBatchExecuteService batchExecuteService;
    private final IBatchScheduleService batchScheduleService;
    private final IHostService hostService;
    private final IBatchTaskLogService batchTaskLogService;
    private final BatchTaskMapper batchTaskMapper;

    @GetMapping
    public String list(Model model) {
        List<BatchSchedule> schedules = batchScheduleService.list();
        Map<String, BatchTask> lastRunMap = new LinkedHashMap<>();
        for (BatchSchedule s : schedules) {
            if (s.getId() == null) continue;
            BatchTask last = batchTaskMapper.selectOne(
                    new LambdaQueryWrapper<BatchTask>()
                            .eq(BatchTask::getScheduleId, s.getId())
                            .orderByDesc(BatchTask::getCreateTime)
                            .last("LIMIT 1"));
            if (last != null) lastRunMap.put(String.valueOf(s.getId()), last);
        }
        model.addAttribute("schedules", schedules);
        model.addAttribute("lastRunMap", lastRunMap);
        return "batch/list";
    }

    @GetMapping("/run")
    public String runPage(Model model) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<Host> hosts = hostService.listForUser(user.getId());
        List<HostGroup> groups = hostService.listGroups();
        Map<String, List<Long>> groupIdToHostIds = new LinkedHashMap<>();
        for (HostGroup g : groups) {
            if (g.getId() == null) continue;
            List<Long> ids = hosts.stream()
                    .filter(h -> g.getId().equals(h.getGroupId()))
                    .map(Host::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            groupIdToHostIds.put(String.valueOf(g.getId()), ids);
        }
        model.addAttribute("hosts", hosts);
        model.addAttribute("groups", groups);
        model.addAttribute("groupIdToHostIds", groupIdToHostIds);
        return "batch/run";
    }

    @PostMapping("/run")
    public String submit(@RequestParam String name, @RequestParam String command, @RequestParam(required = false) String hostIds) {
        if (hostIds == null || hostIds.isBlank()) return "redirect:/batch/run";
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        batchExecuteService.submit(name, command, hostIds, user.getId(), null);
        return "redirect:/batch";
    }

    @GetMapping("/task/{id}")
    public String taskDetail(@PathVariable Long id, Model model) {
        BatchTask task = batchTaskMapper.selectById(id);
        List<BatchTaskLog> logs = batchTaskLogService.listByTaskId(id);
        Map<String, String> hostIdToName = new LinkedHashMap<>();
        for (BatchTaskLog log : logs) {
            if (log.getHostId() == null) continue;
            String key = String.valueOf(log.getHostId());
            if (hostIdToName.containsKey(key)) continue;
            Host host = hostService.getById(log.getHostId());
            String name = host != null ? (host.getName() != null ? host.getName() : host.getHostname()) : null;
            hostIdToName.put(key, name != null ? name : "ID:" + log.getHostId());
        }
        model.addAttribute("task", task != null ? task : new BatchTask());
        model.addAttribute("logs", logs);
        model.addAttribute("hostIdToName", hostIdToName);
        return "batch/detail";
    }

    /** 弹窗用：返回任务日志 JSON（含主机名） */
    @GetMapping("/task/{id}/logs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> taskLogsJson(@PathVariable Long id) {
        BatchTask task = batchTaskMapper.selectById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        List<BatchTaskLog> logs = batchTaskLogService.listByTaskId(id);
        List<Map<String, Object>> list = new ArrayList<>();
        for (BatchTaskLog log : logs) {
            Host host = log.getHostId() != null ? hostService.getById(log.getHostId()) : null;
            String hostName = host != null ? (host.getName() != null ? host.getName() : host.getHostname()) : "ID:" + log.getHostId();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hostId", log.getHostId());
            m.put("hostName", hostName);
            m.put("exitCode", log.getExitCode());
            m.put("output", log.getOutput());
            m.put("error", log.getError());
            m.put("createTime", log.getCreateTime() != null ? log.getCreateTime().getTime() : null);
            list.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task", Map.of(
                "id", task.getId(),
                "name", task.getName() != null ? task.getName() : "",
                "command", task.getCommand() != null ? task.getCommand() : "",
                "status", task.getStatus() != null ? task.getStatus() : 0,
                "successCount", task.getSuccessCount() != null ? task.getSuccessCount() : 0,
                "failCount", task.getFailCount() != null ? task.getFailCount() : 0
        ));
        body.put("logs", list);
        return ResponseEntity.ok(body);
    }

    /** 定时任务：表单片段（弹窗） */
    @GetMapping("/schedule/form")
    public String scheduleForm(@RequestParam(required = false) Long id, Model model) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<Host> hosts = hostService.listForUser(user.getId());
        List<HostGroup> groups = hostService.listGroups();
        Map<String, List<Long>> groupIdToHostIds = new LinkedHashMap<>();
        for (HostGroup g : groups) {
            if (g.getId() == null) continue;
            List<Long> ids = hosts.stream()
                    .filter(h -> g.getId().equals(h.getGroupId()))
                    .map(Host::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            groupIdToHostIds.put(String.valueOf(g.getId()), ids);
        }
        model.addAttribute("hosts", hosts);
        model.addAttribute("groups", groups);
        model.addAttribute("groupIdToHostIds", groupIdToHostIds);
        if (id != null) {
            BatchSchedule schedule = batchScheduleService.getById(id);
            if (schedule != null) {
                model.addAttribute("schedule", schedule);
                model.addAttribute("fragment", true);
                return "batch/schedule-form";
            }
        }
        model.addAttribute("schedule", new BatchSchedule());
        model.addAttribute("fragment", true);
        return "batch/schedule-form";
    }

    @PostMapping("/schedule/save")
    public String scheduleSave(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String command,
            @RequestParam(required = false) List<Long> hostId,
            @RequestParam(required = false) String groupIds,
            @RequestParam(required = false) String cronExpression,
            @RequestParam(required = false) Integer enabled) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        boolean byGroup = groupIds != null && !groupIds.isBlank();
        BatchSchedule schedule = id != null ? batchScheduleService.getById(id) : new BatchSchedule();
        if (schedule == null) schedule = new BatchSchedule();
        schedule.setName(name);
        schedule.setCommand(command);
        if (byGroup) {
            schedule.setGroupIds(groupIds.trim());
            schedule.setHostIds(null);
        } else {
            schedule.setGroupIds(null);
            String hostIds = (hostId != null && !hostId.isEmpty())
                    ? hostId.stream().map(String::valueOf).collect(Collectors.joining(","))
                    : "";
            schedule.setHostIds(hostIds);
        }
        schedule.setCronExpression(cronExpression != null && !cronExpression.isBlank() ? cronExpression.trim() : null);
        schedule.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        batchScheduleService.save(schedule, user.getId());
        return "redirect:/batch";
    }

    @PostMapping("/schedule/delete")
    public String scheduleDelete(@RequestParam Long id) {
        batchScheduleService.removeById(id);
        return "redirect:/batch";
    }

    @PostMapping("/schedule/run-now")
    public String scheduleRunNow(@RequestParam Long id) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        Long taskId = batchScheduleService.runNow(id, user.getId());
        return "redirect:/batch" + (taskId != null ? "#task-" + taskId : "");
    }

    /** 启停切换 */
    @PostMapping("/schedule/toggle")
    public String scheduleToggle(@RequestParam Long id) {
        batchScheduleService.toggle(id);
        return "redirect:/batch";
    }

    /** 某任务最近一次执行的 taskId，供前端「查看日志」用 */
    @GetMapping("/schedule/{id}/last-run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> scheduleLastRun(@PathVariable Long id) {
        BatchTask last = batchTaskMapper.selectOne(
                new LambdaQueryWrapper<BatchTask>()
                        .eq(BatchTask::getScheduleId, id)
                        .orderByDesc(BatchTask::getCreateTime)
                        .last("LIMIT 1"));
        if (last == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("taskId", last.getId()));
    }

    /** 未来 N 次执行计划（根据 Cron 计算），用于列表页定时列悬停提示，默认 3 次 */
    @GetMapping("/schedule/{id}/next-schedule-times")
    @ResponseBody
    public ResponseEntity<List<Long>> scheduleNextTimes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") int count) {
        List<Long> times = batchScheduleService.getNextScheduleTimes(id, count);
        return ResponseEntity.ok(times);
    }

    /** 某任务的执行记录列表（弹窗片段或独立页，支持分页） */
    @GetMapping("/schedule/{id}/runs")
    public String scheduleRuns(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean fragment,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        BatchSchedule schedule = batchScheduleService.getById(id);
        if (schedule == null) {
            return fragment != null && fragment ? "batch/schedule-runs-empty" : "redirect:/batch";
        }
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;
        Page<BatchTask> p = new Page<>(page, size);
        batchTaskMapper.selectPage(p,
                new LambdaQueryWrapper<BatchTask>()
                        .eq(BatchTask::getScheduleId, id)
                        .orderByDesc(BatchTask::getCreateTime));
        model.addAttribute("schedule", schedule);
        model.addAttribute("runs", p.getRecords());
        model.addAttribute("page", (int) p.getCurrent());
        model.addAttribute("size", (int) p.getSize());
        model.addAttribute("total", p.getTotal());
        model.addAttribute("totalPages", (int) p.getPages());
        model.addAttribute("fragment", fragment != null && fragment);
        return "batch/schedule-runs";
    }
}
