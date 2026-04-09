package com.what.deploy.config;

import cn.hutool.core.io.FileUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

@Configuration
@Slf4j
public class InitConfig {

    public static volatile boolean initialized = false;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        
        // 3. 检查初始化状态
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM sys_user", Integer.class);
            initialized = count != null && count > 0;
            log.info("System initialization status: {}", initialized);
            
            // 4. 类型规范化补丁 (Normalization Patch)
            log.info("Applying Resource Type Normalization patch...");
            int updated = jdbcTemplate.update("UPDATE sys_resource SET type = 'COMMON' WHERE type = 'FILE'");
            if (updated > 0) {
                log.info("Migrated {} resource entries from 'FILE' to 'COMMON'", updated);
            }
        } catch (Exception e) {
            log.warn("Failed to check sys_user table (might not exist yet) or apply patches: {}", e.getMessage());
            initialized = false;
        }

        log.info("Initialization complete.");
    }
}

