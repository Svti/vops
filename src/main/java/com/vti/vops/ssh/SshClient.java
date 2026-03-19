package com.vti.vops.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SSH 客户端：基于 JSch，执行命令或打开交互式 Shell
 */
@Slf4j
public class SshClient implements AutoCloseable {

    private final Session session;

    /** 仅用密码认证：只试 password 一种方式 */
    public SshClient(String hostname, int port, String username, String password) throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(username, hostname, port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password");
        session.setConfig("ServerAliveInterval", "30");
        session.setConfig("ServerAliveCountMax", "3");
        session.setPassword(password != null ? password : "");
        session.connect((int) TimeUnit.SECONDS.toMillis(10));
    }

    /** 仅用公钥认证，不尝试 password，避免二次尝试 */
    public SshClient(String hostname, int port, String username, byte[] privateKey, String passphrase) throws JSchException {
        JSch jsch = new JSch();
        jsch.addIdentity("key", privateKey, null, passphrase != null ? passphrase.getBytes(StandardCharsets.UTF_8) : null);
        session = jsch.getSession(username, hostname, port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey");
        session.setConfig("IdentitiesOnly", "yes");
        session.setConfig("ServerAliveInterval", "30");
        session.setConfig("ServerAliveCountMax", "3");
        session.connect((int) TimeUnit.SECONDS.toMillis(10));
    }

    /** 执行命令，返回标准输出；超时秒。为兼容需 TTY 的远程命令（如 docker exec -t），分配 PTY 避免 "the input device is not a TTY" 报错。 */
    public String exec(String command, int timeoutSeconds) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setPty(true); // 分配 PTY，避免远程命令报 "the input device is not a TTY"
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        channel.connect((int) TimeUnit.SECONDS.toMillis(Math.max(1, timeoutSeconds)));
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }
        String result = out.toString(StandardCharsets.UTF_8);
        if (err.size() > 0) {
            result = result + "\n" + err.toString(StandardCharsets.UTF_8);
        }
        channel.disconnect();
        return result.trim();
    }

    public String exec(String command) throws Exception {
        return exec(command, 30);
    }

    /** 保持会话存活，用于连接池心跳 */
    public void heartbeat() {
        try {
            exec("echo", 5);
        } catch (Exception e) {
            log.debug("SSH heartbeat failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * 打开交互式 Shell（PTY），实时收发数据；用于 Web 终端。输出以字节回调以便支持 lrzsz 等二进制协议。
     * 使用给定列/行数设置 PTY 尺寸，使 htop/vim 等全屏程序正确占满终端区域。
     *
     * @param onOutputBytes  服务端输出时回调（原始字节，前端可 base64 传输或解析 ZModem）
     * @param onChannelClosed 通道被对端关闭或读异常时回调（仅调用一次），用于通知前端「连接已断开」
     * @param cols           终端列数（≤0 时用 80）
     * @param rows           终端行数（≤0 时用 24）
     * @return ShellSession，用于写入输入、resize 及关闭
     */
    public ShellSession startShell(Consumer<byte[]> onOutputBytes, Runnable onChannelClosed, int cols, int rows) throws Exception {
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.setPty(true);
        channel.setPtyType("xterm");
        int c = cols > 0 ? cols : 80;
        int r = rows > 0 ? rows : 24;
        channel.setPtySize(c, r, 0, 0);
        channel.connect((int) TimeUnit.SECONDS.toMillis(10));
        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();
        java.util.concurrent.atomic.AtomicBoolean closedNotified = new java.util.concurrent.atomic.AtomicBoolean(false);
        Runnable notifyClosed = () -> {
            if (closedNotified.compareAndSet(false, true) && onChannelClosed != null) {
                try { onChannelClosed.run(); } catch (Exception e) { log.debug("onChannelClosed: {}", e.getMessage()); }
            }
        };
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = in.read(buf)) != -1 && !Thread.currentThread().isInterrupted()) {
                    if (onOutputBytes != null) onOutputBytes.accept(Arrays.copyOf(buf, n));
                }
            } catch (Exception e) {
                if (!channel.isClosed()) log.debug("Shell read: {}", e.getMessage());
            } finally {
                notifyClosed.run();
            }
        }, "ssh-shell-read-" + session.getHost());
        reader.setDaemon(true);
        reader.start();
        return new ShellSession(channel, out, reader);
    }

    /**
     * 交互式 Shell 会话：写入即发送到远端，close 断开通道
     */
    public static class ShellSession implements AutoCloseable {
        private final ChannelShell channel;
        private final OutputStream out;
        private final Thread reader;

        ShellSession(ChannelShell channel, OutputStream out, Thread reader) {
            this.channel = channel;
            this.out = out;
            this.reader = reader;
        }

        public void write(String data) {
            try {
                if (data != null && !data.isEmpty() && out != null) {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception e) {
                log.debug("Shell write: {}", e.getMessage());
            }
        }

        /** 写入原始字节（用于 lrzsz ZModem 等二进制协议） */
        public void writeBytes(byte[] bytes) {
            try {
                if (bytes != null && bytes.length > 0 && out != null) {
                    out.write(bytes);
                    out.flush();
                }
            } catch (Exception e) {
                log.debug("Shell writeBytes: {}", e.getMessage());
            }
        }

        /** 调整 PTY 尺寸（窗口 resize 时由前端通知），使 htop/vim 等全屏程序正确重绘 */
        public void resize(int cols, int rows) {
            try {
                if (channel != null && channel.isConnected() && cols > 0 && rows > 0) {
                    channel.setPtySize(cols, rows, 0, 0);
                }
            } catch (Exception e) {
                log.debug("Shell resize: {}", e.getMessage());
            }
        }

        @Override
        public void close() {
            try {
                if (channel != null && channel.isConnected()) channel.disconnect();
            } catch (Exception e) {
                log.debug("Shell close: {}", e.getMessage());
            }
            if (reader != null) reader.interrupt();
        }
    }
}
