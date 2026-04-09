package com.what.deploy.config;

import cn.hutool.core.io.file.Tailer;
import com.what.deploy.service.NginxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NginxLogWebSocketHandler extends TextWebSocketHandler {

    private final NginxService nginxService;

    public NginxLogWebSocketHandler(NginxService nginxService) {
        this.nginxService = nginxService;
    }

    private final Map<String, Tailer> tailers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        // Extract logType from path: /ws/nginx-log/{logType}
        java.net.URI uri = session.getUri();
        if (uri == null) {
            session.close(new CloseStatus(400, "Missing URI"));
            return;
        }
        String path = uri.getPath();
        String logType = path.substring(path.lastIndexOf("/") + 1);

        if (logType.isEmpty()) {
            logType = "access";
        }

        String logPath = nginxService.getLogPath(logType);
        System.out.println("Nginx Log Connection: type=" + logType + ", path=[" + logPath + "]");

        if (logPath.isEmpty()) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("=== [提示] Nginx 路径未配置，请先在【路径配置】中完成设置 ==="));
            }
            return;
        }

        File logFile = new File(logPath);
        if (!logFile.exists()) {
            session.sendMessage(new TextMessage("=== [系统] 日志文件不存在: " + logPath + " ==="));
            // Don't close, maybe it will be created soon
        } else {
            session.sendMessage(
                    new TextMessage("=== [系统] 已连接到 Nginx " + logType + " 日志 (" + logFile.getName() + ") ==="));

            // Replay last 50 lines
            try {
                java.util.List<String> lastLines = com.what.deploy.util.LogUtil.readLastLines(logFile, 50);
                for (String line : lastLines) {
                    if (session.isOpen() && line != null) {
                        session.sendMessage(new TextMessage(line));
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        Tailer tailer = new Tailer(logFile, line -> {
            try {
                if (session.isOpen() && line != null) {
                    session.sendMessage(new TextMessage(line));
                }
            } catch (Exception e) {
                // ignore
            }
        }, 10);

        tailers.put(session.getId(), tailer);
        tailer.start(true);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Tailer tailer = tailers.remove(session.getId());
        if (tailer != null) {
            tailer.stop();
        }
    }
}
