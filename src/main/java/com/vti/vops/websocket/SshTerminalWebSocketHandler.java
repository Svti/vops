package com.vti.vops.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.User;
import com.vti.vops.service.IAuditLogService;
import com.vti.vops.service.IHostService;
import com.vti.vops.ssh.SshClient;
import com.vti.vops.ssh.SshConnectionPool;
import com.vti.vops.ssh.SshClient.ShellSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH Web 终端：建立与主机的 PTY Shell 通道，xterm.js 与后端双向实时转发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {

    private final SshConnectionPool sshPool;
    private final IAuditLogService auditLogService;
    private final IHostService hostService;

    private final Map<String, SshClient> sessionClients = new ConcurrentHashMap<>();
    private final Map<String, ShellSession> sessionShells = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionHostIds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 不在此处操作 sessionHostIds：hostId 在 handleTextMessage 收到 type=connect 且 SSH 连接成功后才 put，用于 afterConnectionClosed 时 release 连接池
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            ObjectMapper om = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = om.readValue(payload, Map.class);
            String type = (String) map.get("type");
            if ("connect".equals(type)) {
                Object hostIdObj = map.get("hostId");
                if (hostIdObj == null) {
                    sendJson(session, "error", "hostId required");
                    return;
                }
                Long hostId = ((Number) hostIdObj).longValue();
                ShellSession existing = sessionShells.get(session.getId());
                if (existing != null) {
                    Long existingHostId = sessionHostIds.get(session.getId());
                    existing.close();
                    sessionShells.remove(session.getId());
                    sessionClients.remove(session.getId());
                    sessionHostIds.remove(session.getId());
                    if (existingHostId != null) sshPool.closeSession(existingHostId);
                }
                SshConnectionPool.ConnectResult result = sshPool.getClientOrError(hostId);
                if (!result.isSuccess()) {
                    String errMsg = result.error != null ? result.error : "SSH 连接失败";
                    log.info("Terminal connect failed hostId={}, sending error to client: {}", hostId, errMsg);
                    sendJson(session, "error", errMsg);
                    return;
                }
                SshClient client = result.client;
                int cols = intFromMap(map, "cols", 80);
                int rows = intFromMap(map, "rows", 24);
                Runnable onChannelClosed = () -> {
                    sendJson(session, "error", "SSH 连接已断开，请重新连接");
                    ShellSession s = sessionShells.remove(session.getId());
                    if (s != null) s.close();
                    sessionClients.remove(session.getId());
                    Long hid = sessionHostIds.remove(session.getId());
                    if (hid != null) sshPool.closeSession(hid);
                    try { session.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
                };
                ShellSession shell = client.startShell(
                        bytes -> {
                            sendJson(session, "outputBase64", Base64.getEncoder().encodeToString(bytes));
                            sshPool.touch(hostId);
                        },
                        onChannelClosed, cols, rows);
                sessionClients.put(session.getId(), client);
                sessionShells.put(session.getId(), shell);
                sessionHostIds.put(session.getId(), hostId);
                Object userObj = session.getAttributes().get("user");
                if (userObj instanceof User) {
                    User user = (User) userObj;
                    Host host = hostService.getById(hostId);
                    String ip = (String) session.getAttributes().get("clientIp");
                    if (ip == null && session.getRemoteAddress() != null && session.getRemoteAddress().getAddress() != null) {
                        ip = session.getRemoteAddress().getAddress().getHostAddress();
                    }
                    auditLogService.log(user.getId(), user.getUsername(), "host.connect", "host", String.valueOf(hostId), host != null ? host.getName() : null, ip, null);
                }
                sendJson(session, "connected", null);
                return;
            }
            if ("resize".equals(type)) {
                ShellSession shell = sessionShells.get(session.getId());
                Long hid = sessionHostIds.get(session.getId());
                if (shell != null) {
                    int cols = intFromMap(map, "cols", 80);
                    int rows = intFromMap(map, "rows", 24);
                    shell.resize(cols, rows);
                    if (hid != null) sshPool.touch(hid);
                }
                return;
            }
            if ("input".equals(type)) {
                String data = (String) map.get("data");
                ShellSession shell = sessionShells.get(session.getId());
                Long hid = sessionHostIds.get(session.getId());
                if (shell != null && data != null) {
                    shell.write(data);
                    if (hid != null) sshPool.touch(hid);
                }
                return;
            }
            if ("inputBase64".equals(type)) {
                String base64 = (String) map.get("data");
                ShellSession shell = sessionShells.get(session.getId());
                Long hid = sessionHostIds.get(session.getId());
                if (shell != null && base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(base64);
                        if (bytes != null && bytes.length > 0) {
                            shell.writeBytes(bytes);
                            if (hid != null) sshPool.touch(hid);
                        }
                    } catch (Exception ex) {
                        log.debug("inputBase64 decode: {}", ex.getMessage());
                    }
                }
                return;
            }
        } catch (Exception e) {
            log.warn("Terminal ws: {}", e.getMessage());
            sendJson(session, "error", e.getMessage());
        }
    }

    private static int intFromMap(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void sendJson(WebSocketSession session, String type, Object data) {
        try {
            Map<String, Object> m = Map.of("type", type, "data", data != null ? data : "");
            session.sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(m)));
        } catch (Exception e) {
            log.debug("Send ws failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ShellSession shell = sessionShells.remove(session.getId());
        if (shell != null) shell.close();
        sessionClients.remove(session.getId());
        Long hostId = sessionHostIds.remove(session.getId());
        if (hostId != null) sshPool.closeSession(hostId);
    }
}
