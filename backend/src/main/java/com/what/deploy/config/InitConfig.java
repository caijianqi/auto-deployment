package com.what.deploy.config;

import cn.hutool.core.io.FileUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@Slf4j
public class InitConfig {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${deploy.path.backups}")
    private String backupsPath;

    @PostConstruct
    public void init() {
        log.info("Checking and initializing system directories...");

        // 1. Initialize Database Directory
        if (dbUrl.startsWith("jdbc:sqlite:")) {
            String dbFile = dbUrl.substring("jdbc:sqlite:".length());
            File dbDir = new File(dbFile).getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                log.info("Creating database directory: {}", dbDir.getAbsolutePath());
                FileUtil.mkdir(dbDir);
            }
        }
            
        // 2. Initialize Deploy-related Directories
        String[] paths = {backupsPath, "data/logs", "data/tmp"};
        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                log.info("Creating directory: {}", dir.getAbsolutePath());
                FileUtil.mkdir(dir);
            }
        }

        log.info("System directories initialization complete.");
    }
}

