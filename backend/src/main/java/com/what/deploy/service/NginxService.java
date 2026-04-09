package com.what.deploy.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.what.deploy.entity.SystemResource;
import com.what.deploy.mapper.SystemResourceMapper;
import com.what.deploy.util.OSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class NginxService {

    @Autowired
    private SystemResourceMapper resourceMapper;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private NginxBackupService nginxBackupService;

    public String getNginxHome() {
        LambdaQueryWrapper<SystemResource> query = new LambdaQueryWrapper<>();
        query.eq(SystemResource::getCategory, "NGINX").eq(SystemResource::getType, "DIR");
        List<SystemResource> resources = resourceMapper.selectList(query);
        if (resources.isEmpty()) {
            return OSUtil.isWindows() ? "" : "/etc/nginx";
        }
        return resources.get(0).getPath();
    }

    public void saveNginxHome(String path) {
        LambdaQueryWrapper<SystemResource> query = new LambdaQueryWrapper<>();
        query.eq(SystemResource::getCategory, "NGINX").eq(SystemResource::getType, "DIR");
        List<SystemResource> resources = resourceMapper.selectList(query);
        SystemResource resource;
        if (resources.isEmpty()) {
            resource = new SystemResource();
            resource.setCategory("NGINX");
            resource.setType("DIR");
            resource.setPath(path);
            resource.setIsUsed(1); // Mark as used as it's a singleton config
            resource.setRemark("Nginx 安装路径 (内建管理)");
            resourceMapper.insert(resource);
        } else {
            resource = resources.get(0);
            resource.setPath(path);
            resourceMapper.updateById(resource);
        }
    }

    public String getConfigFile() {
        String home = getNginxHome();
        if (home == null || home.isEmpty()) return "";
        
        if (OSUtil.isWindows()) {
            return home + File.separator + "conf" + File.separator + "nginx.conf";
        } else {
            // Linux 下，如果 home 是目录，则尝试找 nginx.conf
            File file = new File(home);
            if (file.isFile()) return home;
            // 常见的 Linux 结构有 /etc/nginx/nginx.conf 或 /usr/local/nginx/conf/nginx.conf
            String[] possibleConfPaths = {
                home + File.separator + "nginx.conf",
                home + File.separator + "conf" + File.separator + "nginx.conf"
            };
            for (String p : possibleConfPaths) {
                if (new File(p).exists()) return p;
            }
            return home + File.separator + "nginx.conf";
        }
    }

    public boolean isRunning() {
        if (OSUtil.isWindows()) {
            String result = RuntimeUtil.execForStr("tasklist /FI \"IMAGENAME eq nginx.exe\"");
            return result.contains("nginx.exe");
        } else {
            // Linux 下检查进程，通常 master process 关键字比较准确
            String result = RuntimeUtil.execForStr("ps -ef");
            return result.contains("nginx: master process");
        }
    }

    public String start() {
        String home = getNginxHome();
        if (OSUtil.isWindows()) {
            if (home.isEmpty()) return "请先配置 Nginx 目录";
            // In Windows, need to run from the directory
            RuntimeUtil.exec(null, new File(home), "cmd", "/c", "start", "nginx.exe");
            return "启动命令已发送";
        } else {
            // Linux 下直接调用 nginx 指令
            try {
                RuntimeUtil.execForStr("nginx");
                return "启动命令已执行";
            } catch (Exception e) {
                return "启动失败：" + e.getMessage();
            }
        }
    }

    public String stop() {
        String home = getNginxHome();
        if (OSUtil.isWindows()) {
            if (home.isEmpty()) return "请先配置 Nginx 目录";
            return RuntimeUtil.execForStr(home + File.separator + "nginx.exe", "-p", home, "-s", "stop");
        } else {
            return RuntimeUtil.execForStr("nginx", "-s", "stop");
        }
    }

    public String reload() {
        String home = getNginxHome();
        if (OSUtil.isWindows()) {
            if (home.isEmpty()) return "请先配置 Nginx 目录";
            return RuntimeUtil.execForStr(home + File.separator + "nginx.exe", "-p", home, "-s", "reload");
        } else {
            return RuntimeUtil.execForStr("nginx", "-s", "reload");
        }
    }

    public String getConfigContent() {
        String path = getConfigFile();
        if (path.isEmpty() || !FileUtil.exist(path)) return "";
        return FileUtil.readUtf8String(path);
    }

    public void saveConfig(String content) {
        String path = getConfigFile();
        if (path.isEmpty()) throw new RuntimeException("Nginx 配置文件路径未配置");
        
        // Auto backup before saving
        nginxBackupService.createBackup(path);
        
        FileUtil.writeUtf8String(content, path);
    }

    public String getLogPath(String type) {
        String home = getNginxHome();
        if (home.isEmpty()) return "";
        // Default log names
        String fileName = "access".equals(type) ? "access.log" : "error.log";
        // Linux 常见的日志路径或者是基于 home 的 logs 目录
        String path = home + File.separator + "logs" + File.separator + fileName;
        if (!new File(path).exists() && OSUtil.isLinux()) {
            // 尝试 Linux 常见的 /var/log/nginx
            String commonLinuxLog = "/var/log/nginx/" + fileName;
            if (new File(commonLinuxLog).exists()) return commonLinuxLog;
        }
        return path;
    }
}
