package com.vti.vops.alert.notifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.alert.AlertMessageFormatter;
import com.vti.vops.alert.AlertNotifierPlugin;
import com.vti.vops.entity.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class FeishuNotifier implements AlertNotifierPlugin {

    private static final String CHANNEL = "feishu";
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public boolean supports(String channel) {
        return CHANNEL.equalsIgnoreCase(channel);
    }

    @Override
    public void send(AlertNotifier notifier, String hostName, String message, int severity) {
        String webhook = getWebhookUrl(notifier.getConfigJson());
        if (webhook == null || webhook.isBlank()) {
            throw new IllegalStateException("Webhook URL 未配置");
        }
        String plainText = AlertMessageFormatter.stripMarkdown(message);
        if (plainText.isEmpty()) plainText = hostName + ": " + message;
        else plainText = AlertMessageFormatter.ALERT_PREFIX + hostName + "\n\n" + plainText;
        Map<String, Object> body = Map.of(
                "msg_type", "text",
                "content", Map.of("text", plainText)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange(webhook, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        if (!r.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("请求失败: " + r.getStatusCode() + " " + (r.getBody() != null ? r.getBody() : ""));
        }
        String respBody = r.getBody();
        if (respBody != null && !respBody.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = om.readValue(respBody, Map.class);
                Object code = json.get("code");
                if (code != null) {
                    int c = code instanceof Number ? ((Number) code).intValue() : Integer.parseInt(String.valueOf(code));
                    if (c != 0) {
                        String msg = json.containsKey("msg") ? String.valueOf(json.get("msg")) : "未知错误";
                        throw new IllegalStateException(msg);
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                log.debug("Parse Feishu response: {}", e.getMessage());
            }
        }
    }

    private String getWebhookUrl(String configJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = om.readValue(configJson, Map.class);
            return m.get("webhook");
        } catch (Exception e) {
            return null;
        }
    }
}
