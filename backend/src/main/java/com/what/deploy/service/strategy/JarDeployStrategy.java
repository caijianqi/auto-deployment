package com.what.deploy.service.strategy;

import cn.hutool.core.io.FileUtil;
import com.what.deploy.entity.AppInfo;
import com.what.deploy.service.manager.ProcessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;

/**
 * JAR 应用部署策略
 */
@Component
@Slf4j
public class JarDeployStrategy implements AppDeployStrategy {

    @Autowired
    private ProcessManager processManager;

    @Override
    public boolean supports(String type) {
        return "JAR".equals(type);
    }

    @Override
    public void start(AppInfo app) {
        long pid = processManager.startJarApp(app);
        app.setPid(pid);
        app.setStatus("RUNNING");
    }

    @Override
    public void stop(AppInfo app) {
        processManager.stopApp(app);
        app.setPid(null);
        app.setStatus("STOPPED");
    }

    @Override
    public void deploy(AppInfo app, MultipartFile file) throws IOException {
        // app.path 直接作为目标 JAR 文件路径（必须以 .jar 结尾）
        File jarFile = new File(app.getPath());
        File destDir = jarFile.getParentFile();
        if (!destDir.exists()) FileUtil.mkdir(destDir);

        // 直接保存到 app.path，覆盖原文件
        file.transferTo(jarFile);
        // app.path 和 deployDir 不再变更，保持用户配置
    }

    @Override
    public void rollback(AppInfo app, String backupFilePath) throws IOException {
        File source = new File(backupFilePath);
        File dest = new File(app.getPath());
        FileUtil.copy(source, dest, true);
    }

    @Override
    public boolean isAlive(AppInfo app) {
        if (processManager.isAlive(app.getPid())) {
            return true;
        }
        // 自动发现：尝试根据路径查找运行中的进程
        long discoveredPid = processManager.findPidByJarPath(app.getPath());
        if (discoveredPid > 0) {
            log.info("Auto-discovered running process for app {}: PID {}", app.getName(), discoveredPid);
            app.setPid(discoveredPid);
            return true;
        }
        return false;
    }

    @Override
    public Integer getActualPort(AppInfo app) {
        return processManager.getActualPort(app.getPid());
    }
}
