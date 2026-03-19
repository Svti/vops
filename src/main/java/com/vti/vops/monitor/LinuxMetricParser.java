package com.vti.vops.monitor;

import lombok.Data;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Linux 命令输出为结构化指标（CPU、内存、磁盘、网络、进程）
 */
public final class LinuxMetricParser {

    /** 解析 /proc/stat 第一行得到 CPU 使用率（需两次采样计算），结果 [0, 100]，保留两位小数。 */
    public static double parseCpuUsage(String statFirstLine, Map<String, long[]> prevCpu) {
        if (statFirstLine == null) return 0;
        String line = statFirstLine.trim();
        if (!line.startsWith("cpu")) return 0;
        String[] parts = line.split("\\s+");
        if (parts.length < 5) return 0;
        int offset = "cpu".equals(parts[0]) ? 1 : 0;
        if (parts.length < offset + 7) return 0;
        long user = parseLongSafe(parts[offset], 0);
        long nice = parseLongSafe(parts[offset + 1], 0);
        long system = parseLongSafe(parts[offset + 2], 0);
        long idle = parseLongSafe(parts[offset + 3], 0);
        long iowait = parseLongSafe(parts[offset + 4], 0);
        long irq = parseLongSafe(parts[offset + 5], 0);
        long softirq = parseLongSafe(parts[offset + 6], 0);
        long total = user + nice + system + idle + iowait + irq + softirq;
        long idleTotal = idle + iowait;
        String key = "cpu";
        long[] prev = prevCpu.get(key);
        if (prev != null && prev[0] > 0) {
            long totalDiff = total - prev[0];
            long idleDiff = idleTotal - prev[1];
            if (totalDiff > 0) {
                double usedDiff = (double) (totalDiff - idleDiff);
                double pct = 100.0 * usedDiff / (double) totalDiff;
                if (pct < 0) pct = 0;
                if (pct > 100) pct = 100;
                prevCpu.put(key, new long[]{total, idleTotal});
                return Math.round(pct * 100.0) / 100.0;
            }
        }
        prevCpu.put(key, new long[]{total, idleTotal});
        return 0;
    }

    private static long parseLongSafe(String s, long def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 解析 free -m 或 free -b 输出 */
    public static MemInfo parseMem(String freeOutput) {
        MemInfo info = new MemInfo();
        // Mem: total used free shared buff/cache available
        Pattern p = Pattern.compile("Mem:\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");
        Matcher m = p.matcher(freeOutput);
        if (m.find()) {
            info.total = Long.parseLong(m.group(1));
            info.used = Long.parseLong(m.group(2));
            info.free = Long.parseLong(m.group(3));
            info.available = Long.parseLong(m.group(6));
            if (info.total > 0) {
                info.usagePercent = 100.0 * (info.total - info.available) / info.total;
            }
        }
        return info;
    }

    @Data
    public static class MemInfo {
        private long total;
        private long used;
        private long free;
        private long available;
        private double usagePercent;
    }

    /**
     * 解析 df -k 输出（1K 块），返回各挂载点使用率 JSON，total/used/available 转为字节存储。
     * 从右往左取列（挂载点、Use%、Available、Used、1K-blocks），避免设备名含空格时列错位导致取不到根路径 /。
     */
    public static String parseDf(String dfOutput) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] lines = dfOutput.split("\n");
        long k = 1024L;
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+");
            if (parts.length < 5) continue;
            try {
                String mount = parts[parts.length - 1];
                String usePctStr = parts[parts.length - 2];
                long availK = Long.parseLong(parts[parts.length - 3]);
                long usedK = Long.parseLong(parts[parts.length - 4]);
                long totalK = Long.parseLong(parts[parts.length - 5]);
                long total = totalK * k;
                long used = usedK * k;
                long avail = availK * k;
                int usePct = usePctStr.endsWith("%") ? Integer.parseInt(usePctStr.replace("%", "")) : (totalK > 0 ? (int) (100.0 * usedK / totalK) : 0);
                Map<String, Object> m = new HashMap<>();
                m.put("mount", mount);
                m.put("total", total);
                m.put("used", used);
                m.put("available", avail);
                m.put("usePercent", usePct);
                list.add(m);
            } catch (NumberFormatException ignored) { }
        }
        return list.isEmpty() ? "[]" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(list).toString();
    }

    /** 解析 /proc/net/dev 得到各网卡收发包与字节 */
    public static String parseNetwork(String netOutput) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] lines = netOutput.split("\n");
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String iface = line.substring(0, colon).trim();
            String[] nums = line.substring(colon + 1).trim().split("\\s+");
            if (nums.length >= 16) {
                try {
                    long rxBytes = Long.parseLong(nums[0]);
                    long txBytes = Long.parseLong(nums[8]);
                    Map<String, Object> m = new HashMap<>();
                    m.put("interface", iface);
                    m.put("rxBytes", rxBytes);
                    m.put("txBytes", txBytes);
                    list.add(m);
                } catch (NumberFormatException ignored) { }
            }
        }
        return list.isEmpty() ? "[]" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(list).toString();
    }

    /** 解析进程数：支持 "ps aux | wc -l" 的单行数字输出，或多行 ps 输出时按行数统计（减去表头 1 行） */
    public static String parseProcessSummary(String psOutput) {
        if (psOutput == null || psOutput.trim().isEmpty()) {
            Map<String, Object> m = new HashMap<>();
            m.put("count", 0);
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(m).toString();
        }
        String trimmed = psOutput.trim();
        int count = 0;
        try {
            int parsed = Integer.parseInt(trimmed.split("\\s+")[0].trim());
            if (parsed > 0) {
                count = parsed - 1;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            int lines = psOutput.split("\n").length;
            count = Math.max(0, lines - 1);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("count", count);
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(m).toString();
    }

    /** 解析 ps -eo pid,user,pcpu,pmem,comm --no-headers --sort=-pcpu 输出，返回 CPU 前 5 进程 JSON 数组 */
    public static String parseProcessTopCpu(String psOutput) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (psOutput == null) return "[]";
        String[] lines = psOutput.trim().split("\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 5);
            if (parts.length >= 4) {
                try {
                    Map<String, Object> m = new HashMap<>();
                    m.put("pid", Integer.parseInt(parts[0]));
                    m.put("user", parts.length > 1 ? parts[1] : "");
                    m.put("cpu", parts.length > 2 ? parseDoubleSafe(parts[2], 0) : 0);
                    m.put("mem", parts.length > 3 ? parseDoubleSafe(parts[3], 0) : 0);
                    m.put("comm", parts.length > 4 ? parts[4].trim() : "");
                    list.add(m);
                } catch (NumberFormatException ignored) { }
            }
        }
        return list.isEmpty() ? "[]" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(list).toString();
    }

    private static double parseDoubleSafe(String s, double def) {
        if (s == null || s.isEmpty()) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
}
