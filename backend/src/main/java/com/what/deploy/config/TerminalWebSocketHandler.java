package com.what.deploy.config;

import cn.hutool.core.util.StrUtil;
import com.what.deploy.util.OSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web 终端 WebSocket 处理器
 * 支持在 Windows (PowerShell / CMD) 以及 Linux (Bash / Sh) 下进行实时命令交互
 */
@Slf4j
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, TerminalProcessContext> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public static class TerminalProcessContext {
        private final Process process;
        private final OutputStream outputStream;
        private final Charset charset;
        private final Thread readerThread;

        public TerminalProcessContext(Process process, OutputStream outputStream, Charset charset, Thread readerThread) {
            this.process = process;
            this.outputStream = outputStream;
            this.charset = charset;
            this.readerThread = readerThread;
        }

        public Process getProcess() {
            return process;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public Charset getCharset() {
            return charset;
        }

        public void destroy() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (process != null && process.isAlive()) {
                // 先销毁所有子进程（setsid 会话中的进程树）
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (Exception ignored) {
                }
                process.destroyForcibly();
            }
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
        }
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        log.info("Terminal WebSocket connection established: session={}", session.getId());

        Map<String, String> queryParams = parseQueryParams(session.getUri());
        String shell = queryParams.getOrDefault("shell", "");
        String dir = queryParams.getOrDefault("dir", "");
        String charsetParam = queryParams.getOrDefault("charset", "");

        // 确定工作目录（默认使用用户家目录 ~）
        String userHome = System.getProperty("user.home");
        File workDir = null;
        if (StrUtil.isNotBlank(dir)) {
            String resolvedDir = dir.trim();
            if (resolvedDir.equals("~")) {
                resolvedDir = userHome;
            } else if (resolvedDir.startsWith("~/") || resolvedDir.startsWith("~\\")) {
                resolvedDir = userHome + resolvedDir.substring(1);
            }
            File target = new File(resolvedDir);
            if (target.exists() && target.isDirectory()) {
                workDir = target;
            }
        }
        if (workDir == null) {
            workDir = new File(userHome);
        }

        // 确定 Shell 命令与编码
        List<String> command = new ArrayList<>();
        Charset charset;
        String shellName; // 记录实际 shell 名称（用于欢迎信息展示）

        if (OSUtil.isWindows()) {
            // Windows 编码策略：中文 Windows CMD/PowerShell 默认是 GBK，若指定则依参数
            if (StrUtil.isNotBlank(charsetParam)) {
                try {
                    charset = Charset.forName(charsetParam);
                } catch (Exception e) {
                    charset = Charset.forName("GBK");
                }
            } else {
                try {
                    charset = Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"));
                } catch (Exception e) {
                    charset = Charset.forName("GBK");
                }
            }

            if ("cmd".equalsIgnoreCase(shell) || "cmd.exe".equalsIgnoreCase(shell)) {
                command.add("cmd.exe");
            } else {
                // 默认使用 powershell.exe
                command.add("powershell.exe");
                command.add("-NoLogo");
                command.add("-NoExit");
            }
            shellName = command.get(0);
        } else {
            // Linux / Unix 默认 UTF-8
            if (StrUtil.isNotBlank(charsetParam)) {
                try {
                    charset = Charset.forName(charsetParam);
                } catch (Exception e) {
                    charset = StandardCharsets.UTF_8;
                }
            } else {
                charset = StandardCharsets.UTF_8;
            }

            if ("sh".equalsIgnoreCase(shell) || "/bin/sh".equalsIgnoreCase(shell)) {
                command.add("/bin/sh");
                command.add("-i");
            } else {
                File bash = new File("/bin/bash");
                if (bash.exists()) {
                    command.add("/bin/bash");
                    command.add("--norc");
                    command.add("--noprofile");
                    command.add("-i");
                } else {
                    command.add("/bin/sh");
                    command.add("-i");
                }
            }
            shellName = command.get(0);

            // 使用 setsid 创建新会话，使子进程脱离父进程的控制终端
            // 防止 -i 模式下子进程的 SIGTTIN/SIGTTOU 信号传播到 Java 父进程导致 Stopped
            for (String setsidPath : new String[]{"/usr/bin/setsid", "/bin/setsid"}) {
                if (new File(setsidPath).exists()) {
                    command.add(0, setsidPath);
                    break;
                }
            }
        }

        // 启动进程
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir.exists() && workDir.isDirectory()) {
            pb.directory(workDir);
        }

        Map<String, String> env = pb.environment();
        env.put("TERM", "xterm-256color");
        if (!OSUtil.isWindows()) {
            // 使用 C.UTF-8 而非 zh_CN.UTF-8，避免服务器未安装中文 locale 时报错
            env.put("LANG", "C.UTF-8");
            env.put("LC_ALL", "C.UTF-8");
            // --norc --noprofile 不会加载 .bashrc，需要手动设置 PS1 提示符
            // 格式: user@host:workdir$ (家目录显示为~)
            env.put("PS1", "\\" + "u@\\h:\\w\\$ ");
        }
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            log.error("Failed to start terminal process: command={}", command, e);
            session.sendMessage(new TextMessage("\u001b[31m[错误] 启动终端进程失败: " + e.getMessage() + "\u001b[0m\r\n"));
            session.close(new CloseStatus(1011, "Failed to start process"));
            return;
        }

        OutputStream processIn = process.getOutputStream();
        InputStream processOut = process.getInputStream();
        final Charset finalCharset = charset;

        // 异步流泵送线程
        Thread readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            // 标记是否仍在启动阶段（仅在启动阶段过滤已知警告）
            boolean startupPhase = true;
            int readCount = 0;
            try {
                int len;
                while ((len = processOut.read(buffer)) != -1) {
                    if (session.isOpen()) {
                        String text = new String(buffer, 0, len, finalCharset);
                        // 启动阶段过滤 setsid 导致的已知 bash 警告
                        if (startupPhase) {
                            text = text.replace("bash: cannot set terminal process group (-1): Inappropriate ioctl for device\n", "");
                            text = text.replace("bash: no job control in this shell\n", "");
                            if (++readCount > 5) {
                                startupPhase = false;
                            }
                        }
                        if (!text.isEmpty()) {
                            synchronized (session) {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage(text));
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
                // 正常退出或流关闭
            } catch (Exception e) {
                log.warn("Error reading terminal output: {}", e.getMessage());
            } finally {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage("\r\n\u001b[33m=== [系统] 终端进程已退出 ===\u001b[0m\r\n"));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "Terminal-Reader-" + session.getId());

        readerThread.setDaemon(true);

        sessions.put(session.getId(), new TerminalProcessContext(process, processIn, charset, readerThread));

        // 必须先发送欢迎信息，再启动 reader 线程
        // 否则 reader 线程和主线程会同时写 WebSocket 导致 TEXT_PARTIAL_WRITING 异常
        String welcome = String.format("\u001b[36m=== 自动化运维平台 Web 终端已连接 ===\u001b[0m\r\n" +
                        "\u001b[90m系统环境: %s | 终端核心: %s | 工作目录: %s | 字符编码: %s\u001b[0m\r\n\r\n",
                OSUtil.getOS(), shellName, workDir.getAbsolutePath(), charset.name());
        session.sendMessage(new TextMessage(welcome));

        // 欢迎信息发送完毕后再启动 reader 线程，保证不会并发写 WebSocket
        readerThread.start();
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        TerminalProcessContext ctx = sessions.get(session.getId());
        if (ctx == null || !ctx.getProcess().isAlive()) {
            return;
        }

        String payload = message.getPayload();
        // 如果客户端发送控制帧（如以 JSON 开头）
        if (payload.startsWith("{") && payload.endsWith("}")) {
            // 可扩展 resize, ping 等指令
            if (payload.contains("\"type\":\"ping\"")) {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                    }
                }
                return;
            }
            if (payload.contains("\"type\":\"input\"")) {
                // 解析 input 字段
                int dataIdx = payload.indexOf("\"data\":");
                if (dataIdx != -1) {
                    // 若有封装直接透传
                }
            }
        }

        // 直接向进程写入输入字符
        byte[] bytes = payload.getBytes(ctx.getCharset());
        try {
            ctx.getOutputStream().write(bytes);
            ctx.getOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to write to terminal stdin: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Terminal WebSocket connection closed: session={}, status={}", session.getId(), status);
        TerminalProcessContext ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.error("Terminal WebSocket transport error: session={}", session.getId(), exception);
        TerminalProcessContext ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.destroy();
        }
    }

    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        if (uri == null || uri.getQuery() == null) {
            return params;
        }
        String[] pairs = uri.getQuery().split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (Exception ignored) {
                }
            }
        }
        return params;
    }
}
