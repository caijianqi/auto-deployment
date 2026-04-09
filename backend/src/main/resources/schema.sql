-- User table
CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    nickname TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- App information table
CREATE TABLE IF NOT EXISTS app_info (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type TEXT NOT NULL, -- JAR or VUE
    path TEXT NOT NULL,
    deploy_dir TEXT, -- 部署目录，存放JAR文件的位置
    config_path TEXT,
    port INTEGER,
    status TEXT DEFAULT 'STOPPED', -- RUNNING, STOPPED, STARTING
    pid INTEGER,
    config_content TEXT,
    version TEXT,
    jdk_id INTEGER,
    is_port_enabled INTEGER DEFAULT 0, -- 0: 禁用, 1: 启用
    last_deploy_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- JDK Configurations
CREATE TABLE IF NOT EXISTS sys_jdk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    path TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Deployment logs
CREATE TABLE IF NOT EXISTS deploy_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER,
    op_type TEXT NOT NULL, -- DEPLOY, UPDATE, START, STOP, ROLLBACK
    content TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- App Backups
CREATE TABLE IF NOT EXISTS app_backup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER,
    version TEXT NOT NULL,
    file_path TEXT NOT NULL,
    size INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Nginx Config Backups
CREATE TABLE IF NOT EXISTS nginx_backup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path TEXT NOT NULL,
    size INTEGER DEFAULT 0,
    remark TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Resource Pool (Deployment Paths)
CREATE TABLE IF NOT EXISTS sys_resource (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL, -- JAR, VUE
    category TEXT DEFAULT 'APP', -- APP: 程序路径, CONFIG: 配置文件路径
    is_used INTEGER DEFAULT 0, -- 0: 可用, 1: 已占用
    config_content TEXT,
    remark TEXT
);
