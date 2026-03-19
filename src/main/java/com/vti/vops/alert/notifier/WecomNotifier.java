package com.vti.vops.alert.notifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.alert.AlertNotifierPlugin;
import com.vti.vops.entity.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class WecomNotifier implements AlertNotifierPlugin {

    private static final String CHANNEL = "wecom";
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
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", message)
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
                Object errcode = json.get("errcode");
                if (errcode != null) {
                    int code = errcode instanceof Number ? ((Number) errcode).intValue() : Integer.parseInt(String.valueOf(errcode));
                    if (code != 0) {
                        String errmsg = json.containsKey("errmsg") ? String.valueOf(json.get("errmsg")) : "未知错误";
                        throw new IllegalStateException(errmsg);
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                log.debug("Parse Wecom response: {}", e.getMessage());
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
