package com.vti.vops.ssh;

import com.vti.vops.config.VopsSshPoolProperties;
import com.vti.vops.entity.Host;
import com.vti.vops.service.IHostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 连接池：按 hostId 缓存 SshClient，复用连接，心跳与淘汰
 */
@Slf4j
@Component
public class SshConnectionPool {

    private final IHostService hostService;
    private final int maxTotal;
    private final long evictIdleMs;

    private final Map<Long, PooledSshClient> pool = new ConcurrentHashMap<>();

    public SshConnectionPool(IHostService hostService, VopsSshPoolProperties poolProps) {
        this.hostService = hostService;
        this.maxTotal = poolProps.getMaxTotal();
        this.evictIdleMs = poolProps.getEvictIdleMs();
    }

    /**
     * 连接结果：成功时 client 非空，失败时 error 非空
     */
    public static class ConnectResult {
        public final SshClient client;
        public final String error;

        public ConnectResult(SshClient client, String error) {
            this.client = client;
            this.error = error;
        }

        public boolean isSuccess() {
            return client != null;
        }
    }

    /** 获取主机 SSH 客户端或错误信息，无则创建并放入池 */
    public ConnectResult getClientOrError(Long hostId) {
        PooledSshClient pooled = pool.get(hostId);
        if (pooled != null && pooled.client.isConnected()) {
            pooled.lastAccess = System.currentTimeMillis();
            return new ConnectResult(pooled.client, null);
        }
        if (pooled != null) {
            pool.remove(hostId);
            try {
                pooled.client.close();
            } catch (Exception e) {
                log.debug("Close stale client: {}", e.getMessage());
            }
        }
        if (pool.size() >= maxTotal) {
            evictOne();
        }
        Host h = hostService.getByIdWithDecryptedCredential(hostId);
        if (h == null) return new ConnectResult(null, "主机不存在或无权访问");
        String cred = h.getCredential();
        if ("privateKey".equalsIgnoreCase(h.getAuthType())) {
            if (cred == null || cred.isBlank()) {
                return new ConnectResult(null, "私钥内容为空，请检查主机是否已选择私钥或私钥库中的密钥是否有效");
            }
        } else {
            if (cred == null) cred = "";
            if (cred.isEmpty()) {
                log.warn("Host id={} password auth but credential length=0 (decrypt failed or not set?), may cause Too many authentication failures", hostId);
            }
        }
        try {
            SshClient client;
            if ("privateKey".equalsIgnoreCase(h.getAuthType())) {
                byte[] keyBytes = normalizePemKey(cred);
                client = new SshClient(h.getHostname(), h.getPort(), h.getUsername(), keyBytes, null);
            } else {
                client = new SshClient(h.getHostname(), h.getPort(), h.getUsername(), cred);
            }
            pool.put(hostId, new PooledSshClient(client));
            return new ConnectResult(client, null);
        } catch (Exception e) {
            log.warn("SSH connect failed hostId={}: {}", hostId, e.getMessage());
            String msg = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                msg = msg + " " + e.getCause().getMessage();
            }
            if (msg == null) msg = "SSH 连接失败";
            if ("privateKey".equalsIgnoreCase(h.getAuthType()) && msg != null
                    && (msg.contains("invalid") && msg.toLowerCase().contains("privatekey"))) {
                msg = msg + "（请确认私钥为 PEM/OpenSSH 格式、无多余空格，或重新粘贴保存后再试）";
            }
            return new ConnectResult(null, msg);
        }
    }

    /** 规范化 PEM 私钥字符串：统一换行为 \\n，去除首尾空白，避免 JSch 因 \\r\\n 报 invalid privatekey */
    private static byte[] normalizePemKey(String pem) {
        if (pem == null) return new byte[0];
        String s = pem.replace("\r\n", "\n").replace("\r", "\n").trim();
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 获取主机 SSH 客户端，无则创建并放入池（兼容旧调用，失败返回 empty） */
    public Optional<SshClient> getClient(Long hostId) {
        ConnectResult r = getClientOrError(hostId);
        return r.isSuccess() ? Optional.of(r.client) : Optional.empty();
    }

    /** 调用方不再占用该主机的连接；连接保留在池中供复用，由定时心跳按 evictIdleMs 淘汰空闲连接。 */
    public void release(Long hostId) {
    }

    /** 显式关闭该主机的 SSH Session 并从连接池移除，用于 Web 终端断开等需要立即释放底层连接的场景。 */
    public void closeSession(Long hostId) {
        if (hostId == null) return;
        PooledSshClient pooled = pool.remove(hostId);
        if (pooled != null) {
            try {
                pooled.client.close();
            } catch (Exception e) {
                log.debug("Close session hostId={}: {}", hostId, e.getMessage());
            }
        }
    }

    /** 使该主机连接失效并从池中移除，下次 getClient 会新建连接。用于采集/执行失败后重试时强制换新连接。 */
    public void invalidate(Long hostId) {
        PooledSshClient pooled = pool.remove(hostId);
        if (pooled != null) {
            try {
                pooled.client.close();
            } catch (Exception e) {
                log.debug("Close invalidated client hostId={}: {}", hostId, e.getMessage());
            }
        }
    }

    /** 标记该主机连接最近被使用，避免心跳任务在使用中误淘汰（如 Web 终端有输入/输出时调用）。 */
    public void touch(Long hostId) {
        if (hostId == null) return;
        PooledSshClient p = pool.get(hostId);
        if (p != null) p.lastAccess = System.currentTimeMillis();
    }

    private void evictOne() {
        Optional<Map.Entry<Long, PooledSshClient>> oldest = pool.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess));
        oldest.ifPresent(e -> {
            pool.remove(e.getKey());
            try {
                e.getValue().client.close();
            } catch (Exception ex) {
                log.debug("Evict close: {}", ex.getMessage());
            }
        });
    }

    @Scheduled(fixedDelayString = "${vops.ssh.pool.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        long now = System.currentTimeMillis();
        pool.forEach((hostId, pooled) -> {
            if (now - pooled.lastAccess > evictIdleMs) {
                pool.remove(hostId);
                try {
                    pooled.client.close();
                } catch (Exception e) {
                    log.debug("Evict idle: {}", e.getMessage());
                }
            } else {
                pooled.client.heartbeat();
            }
        });
    }

    private static class PooledSshClient {
        final SshClient client;
        volatile long lastAccess = System.currentTimeMillis();

        PooledSshClient(SshClient client) {
            this.client = client;
        }
    }
}
