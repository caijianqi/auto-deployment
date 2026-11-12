package com.what.deploy.config;

import cn.hutool.core.util.StrUtil;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 终端 WebSocket 处理器（基于 pty4j 原生伪终端）
 * 支持在 Windows (ConPTY / PowerShell / CMD) 以及 Linux (PTS / Bash / Sh) 下进行原生终端交互
 */
@Slf4j
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, TerminalProcessContext> sessions = new ConcurrentHashMap<>();

    public static class TerminalProcessContext {
        private final PtyProcess process;
        private final OutputStream outputStream;
        private final Charset charset;
        private final Thread readerThread;

        public TerminalProcessContext(PtyProcess process, OutputStream outputStream, Charset charset, Thread readerThread) {
            this.process = process;
            this.outputStream = outputStream;
            this.charset = charset;
            this.readerThread = readerThread;
        }

        public PtyProcess getProcess() {
            return process;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public Charset getCharset() {
            return charset;
        }

        public void resize(int cols, int rows) {
            if (process != null && process.isAlive()) {
                try {
                    int safeCols = Math.max(10, Math.min(cols, 1000));
                    int safeRows = Math.max(3, Math.min(rows, 500));
                    process.setWinSize(new WinSize(safeCols, safeRows));
                } catch (Exception e) {
                    log.warn("Failed to resize pty window size to {}x{}: {}", cols, rows, e.getMessage());
                }
            }
        }

        public void destroy() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (process != null && process.isAlive()) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
            }
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
        }
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        // 双重安全校验：确认握手阶段已注入合法登录 ID
        Object loginId = session.getAttributes().get("loginId");
        if (loginId == null) {
            log.warn("Terminal connection rejected: missing authenticated loginId, session={}", session.getId());
            session.close(new CloseStatus(4401, "Unauthorized"));
            return;
        }

        log.info("Terminal WebSocket connection established: session={}, user={}", session.getId(), loginId);

        Map<String, String> queryParams = parseQueryParams(session.getUri());
        String shell = queryParams.getOrDefault("shell", "");
        String dir = queryParams.getOrDefault("dir", "");
        String charsetParam = queryParams.getOrDefault("charset", "");
        int initialCols = parsePositiveInt(queryParams.get("cols"), 80);
        int initialRows = parsePositiveInt(queryParams.get("rows"), 24);

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
        String shellName;

        if (OSUtil.isWindows()) {
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
                command.add("powershell.exe");
                command.add("-NoLogo");
            }
            shellName = command.get(0);
        } else {
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
                    command.add("-i");
                } else {
                    command.add("/bin/sh");
                    command.add("-i");
                }
            }
            shellName = command.get(0);
        }

        // 构建 PTY 环境变量
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        if (!OSUtil.isWindows()) {
            env.put("LANG", "C.UTF-8");
            env.put("LC_ALL", "C.UTF-8");
        }

        PtyProcess process;
        try {
            PtyProcessBuilder ppb = new PtyProcessBuilder()
                    .setCommand(command.toArray(new String[0]))
                    .setEnvironment(env)
                    .setInitialColumns(initialCols)
                    .setInitialRows(initialRows)
                    .setConsole(false);

            if (workDir.exists() && workDir.isDirectory()) {
                ppb.setDirectory(workDir.getAbsolutePath());
            }

            process = ppb.start();
        } catch (Exception e) {
            log.error("Failed to start pty terminal process: command={}", command, e);
            session.sendMessage(new TextMessage("\u001b[31m[错误] 启动伪终端进程失败: " + e.getMessage() + "\u001b[0m\r\n"));
            session.close(new CloseStatus(1011, "Failed to start pty process"));
            return;
        }

        OutputStream processIn = process.getOutputStream();
        InputStream processOut = process.getInputStream();
        final Charset finalCharset = charset;

        // 异步流读取泵送线程
        Thread readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int len;
                while ((len = processOut.read(buffer)) != -1) {
                    if (session.isOpen()) {
                        String text = new String(buffer, 0, len, finalCharset);
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
                // 正常进程关闭或流结束
            } catch (Exception e) {
                log.warn("Error reading pty terminal output: {}", e.getMessage());
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

        // 发送终端连接成功横幅
        String welcome = String.format("\u001b[36m=== 自动化运维平台 Web 终端已连接 (PTY Native) ===\u001b[0m\r\n" +
                        "\u001b[90m系统环境: %s | 终端核心: %s | 工作目录: %s | 字符编码: %s\u001b[0m\r\n\r\n",
                OSUtil.getOS(), shellName, workDir.getAbsolutePath(), charset.name());
        session.sendMessage(new TextMessage(welcome));

        readerThread.start();
    }

    public static final String CTRL_PREFIX = "__CTRL__:";

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        TerminalProcessContext ctx = sessions.get(session.getId());
        if (ctx == null || !ctx.getProcess().isAlive()) {
            return;
        }

        String payload = message.getPayload();
        if (StrUtil.isBlank(payload)) {
            return;
        }

        String trimmed = payload.trim();

        // 1. 兼容前缀控制帧（若前端使用带前缀的控制包）
        if (trimmed.startsWith(CTRL_PREFIX)) {
            String json = trimmed.substring(CTRL_PREFIX.length()).trim();
            handleControlFrame(session, ctx, json);
            return;
        }

        // 2. 识别标准纯 JSON 控制帧（{"type":"resize", ...} 或 {"type":"ping"}）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (trimmed.contains("\"type\":\"ping\"") || trimmed.contains("\"type\":\"resize\"")) {
                handleControlFrame(session, ctx, trimmed);
                return;
            }
        }

        // 3. 原生按键输入透传至 PTY
        byte[] bytes = payload.getBytes(ctx.getCharset());
        try {
            ctx.getOutputStream().write(bytes);
            ctx.getOutputStream().flush();
        } catch (IOException e) {
            log.warn("Failed to write to pty stdin: {}", e.getMessage());
        }
    }

    private void handleControlFrame(WebSocketSession session, TerminalProcessContext ctx, String json) throws IOException {
        if (json.contains("\"type\":\"ping\"")) {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                }
            }
            return;
        }
        if (json.contains("\"type\":\"resize\"")) {
            int cols = extractJsonInt(json, "cols", 80);
            int rows = extractJsonInt(json, "rows", 24);
            ctx.resize(cols, rows);
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

    private int parsePositiveInt(String val, int defaultVal) {
        if (StrUtil.isBlank(val)) {
            return defaultVal;
        }
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private int extractJsonInt(String json, String field, int defaultVal) {
        try {
            Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return defaultVal;
    }
}
