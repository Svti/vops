package com.vti.vops.alert;

/**
 * 告警消息格式化：Markdown、纯文本、HTML，供各通知渠道使用
 */
public final class AlertMessageFormatter {

    /** 告警标题（用于 Markdown 标题、钉钉消息标题等），统一在此修改 */
    public static final String ALERT_TITLE = "运维告警";

    /** 告警前缀（用于邮件主题、飞书等纯文本前缀），如 [运维告警] */
    public static final String ALERT_PREFIX = "[" + ALERT_TITLE + "] ";

    /**
     * 生成带层级的 Markdown 告警内容（钉钉/企微等支持 markdown 的渠道直接使用）
     */
    public static String formatMarkdown(String severityLabel, String ruleName, String hostName, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(ALERT_TITLE).append("\n\n");
        sb.append("**严重等级** ").append(severityLabel).append("\n\n");
        sb.append("**规则名称** ").append(escapeMd(ruleName)).append("\n\n");
        sb.append("**主机** ").append(escapeMd(hostName)).append("\n\n");
        sb.append("**说明** ").append(escapeMd(detail)).append("\n");
        return sb.toString();
    }

    /**
     * 将 Markdown 转为纯文本（去掉加粗、标题等，飞书等仅支持纯文本时使用）
     */
    public static String stripMarkdown(String markdown) {
        if (markdown == null) return "";
        String s = markdown
                .replaceAll("^##?\\s+", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("^[-*]\\s+", "")
                .trim();
        return s.replace("\n\n\n", "\n\n");
    }

    /**
     * 将 Markdown 转为简单 HTML（邮件正文等使用）
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null) return "";
        String s = markdown;
        s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?m)^## (.+)$", "<h2 style='margin:12px 0 8px 0;font-size:16px;'>$1</h2>");
        s = s.replace("\n\n", "</p><p style='margin:6px 0;'>");
        s = s.replace("\n", "<br/>");
        return "<div style='font-family:sans-serif;font-size:14px;line-height:1.6;'>" + "<p style='margin:6px 0;'>" + s + "</p></div>";
    }

    private static String escapeMd(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("[", "\\[").replace("#", "\\#");
    }
}
