package com.what.deploy.service.impl;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.what.deploy.model.JavaProcessInfo;
import com.what.deploy.service.JavaProcessService;
import com.what.deploy.util.OSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JavaProcessServiceImpl implements JavaProcessService {

    @Override
    public List<JavaProcessInfo> listProcesses() {
        List<JavaProcessInfo> results = new ArrayList<>();
        try {
            // 使用 jps -l -v 获取详细信息
            // -l: 输出主类全名或 jar 路径
            // -v: 输出传递给 JVM 的参数
            List<String> output = RuntimeUtil.execForLines("jps", "-l", "-v");
            
            long currentPid = ProcessHandle.current().pid();

            for (String line : output) {
                if (StrUtil.isBlank(line)) continue;
                
                String[] parts = line.trim().split("\\s+", 3);
                if (parts.length < 1) continue;

                String pid = parts[0];
                // 排除 jps 自身进程
                if (line.contains("sun.tools.jps.Jps")) continue;
                
                // 过滤掉平台自身的进程
                if (String.valueOf(currentPid).equals(pid)) continue;

                String mainClass = parts.length > 1 ? parts[1] : "Unknown";
                String arguments = parts.length > 2 ? parts[2] : "";

                results.add(JavaProcessInfo.builder()
                        .pid(pid)
                        .mainClass(mainClass)
                        .arguments(arguments)
                        .isCurrentApp(String.valueOf(currentPid).equals(pid))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to list java processes", e);
        }
        return results;
    }

    @Override
    public void killProcess(String pid) {
        long currentPid = ProcessHandle.current().pid();
        if (String.valueOf(currentPid).equals(pid)) {
            throw new RuntimeException("不能终止平台自身进程");
        }

        log.info("Killing java process with pid: {}", pid);
        if (OSUtil.isWindows()) {
            RuntimeUtil.exec("taskkill", "/F", "/PID", pid);
        } else {
            RuntimeUtil.exec("kill", "-9", pid);
        }
    }
}
