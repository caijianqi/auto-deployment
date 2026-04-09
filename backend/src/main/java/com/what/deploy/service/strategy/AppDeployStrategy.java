package com.what.deploy.service.strategy;

import com.what.deploy.entity.AppInfo;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * 部署策略接口，定义不同类型应用（JAR, VUE等）的公用操作
 */
public interface AppDeployStrategy {
    
    /**
     * 判断当前策略是否支持该应用类型
     */
    boolean supports(String type);

    /**
     * 启动应用
     */
    void start(AppInfo app);

    /**
     * 停止应用
     */
    void stop(AppInfo app);

    /**
     * 部署新版本
     */
    void deploy(AppInfo app, MultipartFile file) throws IOException;

    /**
     * 回滚到指定文件
     */
    void rollback(AppInfo app, String backupFilePath) throws IOException;

    /**
     * 检查进程是否在线
     */
    boolean isAlive(AppInfo app);

    /**
     * 获取实际运行端口（如果适用）
     */
    default Integer getActualPort(AppInfo app) {
        return null;
    }
}
