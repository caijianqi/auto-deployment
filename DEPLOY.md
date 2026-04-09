# Deployment Documentation | 部署手册

本文档详细介绍了如何将 Auto-Deployment 平台部署至 Linux 服务器环境。

## 1. 后端部署 (Spring Boot)

### 1.1 打包
在 `backend` 目录下执行：
```bash
mvn clean package -DskipTests
```
产物位于 `target/auto-deployment-0.0.1-SNAPSHOT.jar`。

### 1.2 启动
建议使用 nohup 或 systemd 运行。首次启动会生成 `data/` 目录和 `auto-deploy.db` 数据库文件。
```bash
nohup java -jar auto-deployment-0.0.1-SNAPSHOT.jar > /dev/null 2>&1 &
```

---

## 2. 前端部署 (Vue 3)

### 2.1 修改配置 (可选)
如果您的后端接口不是通过 Nginx 转发，请修改 `src/api/index.js` 中的 `baseURL`。

### 2.2 打包
在 `frontend` 目录下执行：
```bash
yarn build
# 或 npm run build
```
产物位于 `dist/` 目录。

---

## 3. Nginx 核心配置 (推荐)

为了支持 WebSocket 日志、大文件上传以及子路径访问，请参考以下 Nginx 配置。

```nginx
# 全局设置，建议放在 http 段或 server 段
client_max_body_size 500M; # 允许上传大容量的 JAR/ZIP 包

server {
    listen 80;
    server_name 192.168.203.203; # 您的服务器 IP 或 域名

    # 1. 前端静态资源 (子路径 /auto-ui/)
    location /auto-ui/ {
        alias /path/to/your/frontend/dist/; # 指向前端 dist 目录的绝对路径
        index index.html;
        try_files $uri $uri/ /auto-ui/index.html;
    }

    # 2. 后端 API 转发
    location /auto/api/ {
        proxy_pass http://127.0.0.1:8080/; # 后端 SpringBoot 默认端口
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # 支持 WebSocket (关键！)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 延长超时时间以防大文件上传失败
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
```

---

## 4. 常见问题 (FAQ)

### 4.1 页面显示 502 Bad Gateway
- 请检查后端 Spring Boot 是否已成功启动并在 8080 端口监听。
- 检查 Nginx 日志 `error.log` 查看转发详情。

### 4.2 WebSocket 连接失败
- 确保 Nginx 配置中包含 `Upgrade` 和 `Connection` 请求头。
- 如果部署在 `/auto-ui/` 下，请确保 API 前缀配置为 `/auto/api`。

### 4.3 无法通过路径发现 Java 进程
- 请确保后端服务有权限执行 `ps` 或 `ss` 指令。
- 如果是 Windows 环境，请确保 `wmic` 可用。

### 4.4 无法读取 YAML 文件内容
- 检查配置文件路径是否正确。
- 确保后端进程对该文件有读取和写入权限。
