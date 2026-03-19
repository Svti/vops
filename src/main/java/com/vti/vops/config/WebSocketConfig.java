package com.vti.vops.config;

import com.vti.vops.entity.User;
import com.vti.vops.websocket.MetricWebSocketHandler;
import com.vti.vops.websocket.SshTerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricWebSocketHandler metricWebSocketHandler;
    private final SshTerminalWebSocketHandler sshTerminalWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricWebSocketHandler, "/ws/metric").setAllowedOrigins("*");
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                .setAllowedOrigins("*")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
                        Subject subject = SecurityUtils.getSubject();
                        Object principal = subject != null ? subject.getPrincipal() : null;
                        if (principal instanceof User) {
                            attributes.put("user", principal);
                        }
                        String clientIp = resolveClientIp(request);
                        if (clientIp != null) {
                            attributes.put("clientIp", clientIp);
                        }
                        return null;
                    }
                });
    }

    /**
     * 从握手请求解析真实客户端 IP（与 Controller 中 getClientIp 语义一致，便于反向代理后记录外网 IP）。
     * 优先级：X-Forwarded-For 第一个 → X-Real-IP → request.getRemoteAddress()
     */
    private static String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }
        String xri = request.getHeaders().getFirst("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri.trim();
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }
}
