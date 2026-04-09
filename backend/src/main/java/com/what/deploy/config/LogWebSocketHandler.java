package com.what.deploy.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import com.what.deploy.entity.AppInfo;
import com.what.deploy.mapper.AppInfoMapper;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Tailer> tailers = new ConcurrentHashMap<>();
    private final AppInfoMapper appInfoMapper;

    public LogWebSocketHandler(AppInfoMapper appInfoMapper) {
        this.appInfoMapper = appInfoMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Object appIdObj = session.getAttributes().get("appId");
        if (appIdObj == null) {
            session.close(new CloseStatus(400, "Missing appId"));
            return;
        }

        Integer appId;
        try {
            appId = Integer.parseInt(appIdObj.toString());
        } catch (Exception e) {
            session.close(new CloseStatus(400, "Invalid appId format"));
            return;
        }

        AppInfo appInfo = appInfoMapper.selectById(appId);
        if (appInfo == null) {
            session.close(new CloseStatus(404, "Application not found"));
            return;
        }

        String appName = appInfo.getName();

        if (session.isOpen()) {
            session.sendMessage(new TextMessage("=== [系统] 已建立实时日志连接 (" + appName + ") ==="));
        }

        // 修改为 ID 命名读取逻辑，规避中文名导致的读取失败
        File logFile = new File("data/logs/" + appId + ".log");
        if (!logFile.exists()) {
            FileUtil.touch(logFile);
        } else {
            // Replay last 100 lines for context
            try {
                java.util.List<String> lastLines = com.what.deploy.util.LogUtil.readLastLines(logFile, 100);
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
