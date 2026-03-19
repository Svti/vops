package com.vti.vops.alert.notifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.alert.AlertMessageFormatter;
import com.vti.vops.alert.AlertNotifierPlugin;
import com.vti.vops.entity.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
@Slf4j
public class DingTalkNotifier implements AlertNotifierPlugin {

    private static final String CHANNEL = "dingtalk";
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public boolean supports(String channel) {
        return CHANNEL.equalsIgnoreCase(channel);
    }

    @Override
    public void send(AlertNotifier notifier, String hostName, String message, int severity) {
        String webhook = getConfigValue(notifier.getConfigJson(), "webhook");
        String secret = getConfigValue(notifier.getConfigJson(), "secret");
        if (webhook == null || webhook.isBlank()) {
            throw new IllegalStateException("Webhook URL 未配置");
        }
        String url = buildSignedUrl(webhook.trim(), secret != null ? secret.trim() : null);
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "title", AlertMessageFormatter.ALERT_TITLE,
                        "text", message
                )
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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
                log.debug("Parse DingTalk response: {}", e.getMessage());
            }
        }
    }

    private String getConfigValue(String configJson, String key) {
        if (configJson == null || configJson.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = om.readValue(configJson, Map.class);
            return m.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 钉钉加签：timestamp(毫秒) + "\\n" + secret 做 HMAC-SHA256，Base64 后 URL 编码，拼到 URL */
    private String buildSignedUrl(String webhook, String secret) {
        if (secret == null || secret.isBlank()) {
            return webhook;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.getEncoder().encodeToString(signBytes);
            sign = java.net.URLEncoder.encode(sign, StandardCharsets.UTF_8);
            String sep = webhook.contains("?") ? "&" : "?";
            return webhook + sep + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            throw new IllegalStateException("加签失败: " + e.getMessage());
        }
    }
}
