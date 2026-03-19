package com.vti.vops.alert.notifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.alert.AlertMessageFormatter;
import com.vti.vops.alert.AlertNotifierPlugin;
import com.vti.vops.entity.AlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.internet.MimeMessage;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MailNotifier implements AlertNotifierPlugin {

    private static final String CHANNEL = "email";
    private final JavaMailSender mailSender;
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public boolean supports(String channel) {
        return CHANNEL.equalsIgnoreCase(channel);
    }

    @Override
    public void send(AlertNotifier notifier, String hostName, String message, int severity) {
        String to = getTo(notifier.getConfigJson());
        if (to == null) return;
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(AlertMessageFormatter.ALERT_PREFIX + hostName);
            helper.setText(AlertMessageFormatter.markdownToHtml(message), true);
            mailSender.send(mime);
        } catch (Exception e) {
            throw new IllegalStateException("发送邮件失败: " + (e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private String getTo(String configJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = om.readValue(configJson, Map.class);
            return m.get("to");
        } catch (Exception e) {
            return null;
        }
    }
}
