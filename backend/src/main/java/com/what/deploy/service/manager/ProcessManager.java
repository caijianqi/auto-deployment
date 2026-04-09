package com.what.deploy.service.manager;

import com.what.deploy.entity.AppInfo;
import com.what.deploy.entity.SystemJdk;
import com.what.deploy.mapper.SystemJdkMapper;
import com.what.deploy.util.OSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程管理器：负责应用的启动、停止及状态监控
 */
@Service
@Slf4j
public class ProcessManager {
    
    @Autowired
    private SystemJdkMapper jdkMapper;

    private final Map<Integer, Process> activeProcesses = new ConcurrentHashMap<>();

    /**
     * 启动 JAR 应用（后台运行，不受部署平台关闭影响）
     * 通过 cmd /c start 或 setsid 彻底脱离父进程会话
     */
    public long startJarApp(AppInfo app) {
        try {
            // 1. JDK 路径选择
            String javaPath = "java";
            if (app.getJdkId() != null) {
                SystemJdk jdk = jdkMapper.selectById(app.getJdkId());
                if (jdk != null && cn.hutool.core.util.StrUtil.isNotBlank(jdk.getPath())) {
                    javaPath = jdk.getPath();
                }
            }

            // 构建 Java 启动命令参数列表
            List<String> commands = new ArrayList<>();
            commands.add(javaPath);
            commands.add("-Dfile.encoding=UTF-8");
            commands.add("-jar");
            commands.add(app.getPath());

            // 端口启动开关控制
            if (app.getIsPortEnabled() != null && app.getIsPortEnabled() == 1) {
                if (app.getPort() != null && app.getPort() > 0) {
                    commands.add("--server.port=" + app.getPort());
                }
            }

            if (cn.hutool.core.util.StrUtil.isNotBlank(app.getConfigPath())) {
                commands.add("--spring.config.location=file:" + app.getConfigPath());
            }

            String workDir = new File(app.getPath()).getParentFile().getAbsolutePath();

            // 2. 日志文件
            File logFile = new File("data/logs/" + app.getId() + ".log");
            if (!logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();

            long pid;
            if (OSUtil.isWindows()) {
                // Windows: 使用 cmd /c start 在新会话中启动，彻底脱离父进程
                pid = startOnWindows(app, commands, workDir, logFile);
            } else {
                // Linux: 使用 setsid 创建新会话，完全脱离父进程
                pid = startOnLinux(app, commands, workDir, logFile);
            }

            if (pid <= 0) {
                throw new RuntimeException("无法获取 JAR 应用进程ID");
            }

            log.info("Started JAR application: {} (PID: {})", app.getName(), pid);
            return pid;
        } catch (Exception e) {
            log.error("Failed to start JAR application: {}", app.getName(), e);
            throw new RuntimeException("启动应用失败: " + e.getMessage());
        }
    }

    private long startOnWindows(AppInfo app, List<String> commands, String workDir, File logFile) throws Exception {
        // cmd /c start "" - 创建新窗口会话，cmd进程退出后Java进程独立运行
        // 注意：不能在这里用 >> 重定向，因为 start 不支持管道
        // Java 进程的输出会直接到控制台窗口
        StringBuilder cmdLine = new StringBuilder();
        cmdLine.append("cmd /c start \"\" /d \"").append(workDir).append("\"");
        for (String arg : commands) {
            cmdLine.append(" \"").append(arg).append("\"");
        }

        log.info("Executing: {}", cmdLine);
        Process startProcess = Runtime.getRuntime().exec(cmdLine.toString());
        startProcess.waitFor();

        // 查询实际 PID
        return findPidByJarPath(app.getPath());
    }

    private long startOnLinux(AppInfo app, List<String> commands, String workDir, File logFile) throws Exception {
        StringBuilder cmdLine = new StringBuilder();
        for (String arg : commands) {
            cmdLine.append(" '").append(arg.replace("'", "'\\''")).append("'");
        }
        cmdLine.append(" >> '").append(logFile.getAbsolutePath()).append("' 2>&1");

        // setsid 创建新会话，父进程退出后Java进程继续运行
        String setsidCmd = String.format("cd '%s' && setsid%s &",
                workDir, cmdLine.toString());

        log.info("Executing: {}", setsidCmd);
        Process startProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", setsidCmd});
        startProcess.waitFor();

        // 查询实际 PID
        return findPidByJarPath(app.getPath());
    }

    /**
     * 根据 JAR 路径查找正在运行的 PID
     * @param jarPath JAR 文件的绝对路径或相对路径
     * @return 查找到的第一个 PID，未找到返回 -1
     */
    public long findPidByJarPath(String jarPath) {
        if (cn.hutool.core.util.StrUtil.isBlank(jarPath)) return -1;
        
        String jarName = new File(jarPath).getName();
        if (OSUtil.isWindows()) {
            return queryPidOnWindows(jarName);
        } else {
            return queryPidOnLinux(jarName);
        }
    }

    private long queryPidOnWindows(String jarName) {
        try {
            // Windows: 使用 wmic 查询命令行内容中包含 JAR 名称的进程
            String command = "cmd /c wmic process where \"commandline like '%" + jarName + "%'\" get processid";
            Process proc = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.equalsIgnoreCase("ProcessId")) continue;
                    try {
                        return Long.parseLong(line);
                    } catch (NumberFormatException e) {
                        // 忽略非数字行
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query PID on Windows for {}: {}", jarName, e.getMessage());
        }
        return -1;
    }

    private long queryPidOnLinux(String jarName) {
        try {
            // Linux: 结合 ps -ef 和 grep 查找，排除 grep 自身
            String[] cmd = {"sh", "-c", "ps -ef | grep '" + jarName + "' | grep -v grep | awk '{print $2}' | head -1"};
            Process proc = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String pidStr = reader.readLine();
                if (pidStr != null && !pidStr.trim().isEmpty()) {
                    return Long.parseLong(pidStr.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query PID on Linux for {}: {}", jarName, e.getMessage());
        }
        return -1;
    }

    /**
     * 停止应用进程
     */
    public void stopApp(AppInfo app) {
        Process process = activeProcesses.remove(app.getId());
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("Stopped application via handle: {}", app.getName());
        } else if (app.getPid() != null && app.getPid() > 0) {
            // 尝试通过 PID 销毁（兼容重启后的场景）
            java.lang.ProcessHandle.of(app.getPid()).ifPresent(handle -> {
                handle.destroy();
                log.info("Stopped application via PID {}: {}", app.getPid(), app.getName());
            });
        }
    }

    /**
     * 检查进程是否存活
     */
    public boolean isAlive(Long pid) {
        if (pid == null || pid <= 0) return false;
        return java.lang.ProcessHandle.of(pid)
                .map(java.lang.ProcessHandle::isAlive)
                .orElse(false);
    }

    /**
     * 获取端口号
     */
    public Integer getActualPort(Long pid) {
        if (pid == null || pid <= 0) return null;
        try {
            if (OSUtil.isWindows()) {
                // Windows: netstat -ano
                String command = "cmd /c netstat -ano";
                Process process = Runtime.getRuntime().exec(command);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    String pidStr = String.valueOf(pid);
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.contains("LISTENING") || !line.contains(pidStr)) continue;
                        
                        String[] parts = line.split("\\s+");
                        // 最后一列必须是 PID
                        if (parts.length >= 5 && parts[parts.length - 1].equals(pidStr)) {
                            String address = parts[1]; // 本地地址
                            int lastColon = address.lastIndexOf(":");
                            if (lastColon != -1) {
                                return Integer.parseInt(address.substring(lastColon + 1));
                            }
                        }
                    }
                }
            } else {
                // Linux: ss -lntp 或 netstat -lntp
                // 尝试匹配格式如: ,pid=5415, 或 "java",5415, 或 pid=5415)
                String[] commands = {"sh", "-c", "ss -lntp || netstat -lntp"};
                Process process = Runtime.getRuntime().exec(commands);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    String pidStr = String.valueOf(pid);
                    while ((line = reader.readLine()) != null) {
                        // 匹配包含该 PID 的行，通常在 users:(...) 字段中
                        if (line.contains("pid=" + pidStr + ",") || line.contains("," + pidStr + ",") || line.contains("pid=" + pidStr + ")")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length >= 4) {
                                String address = parts[3];
                                int lastColon = address.lastIndexOf(":");
                                if (lastColon != -1) {
                                    return Integer.parseInt(address.substring(lastColon + 1));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch actual port for PID {}: {}", pid, e.getMessage());
        }
        return null;
    }
}
