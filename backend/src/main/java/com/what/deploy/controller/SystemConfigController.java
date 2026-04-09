package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.what.deploy.entity.SystemResource;
import com.what.deploy.entity.SystemJdk;
import com.what.deploy.mapper.SystemResourceMapper;
import com.what.deploy.mapper.SystemJdkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    @Autowired
    private SystemResourceMapper resourceMapper;

    @Autowired
    private SystemJdkMapper jdkMapper;

    @GetMapping("/resources")
    public SaResult getResources(@RequestParam(required = false) String type, 
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) Boolean availableOnly) {
        LambdaQueryWrapper<SystemResource> query = new LambdaQueryWrapper<>();
        if (type != null) {
            if ("CONFIG".equals(category)) {
                // 对于配置文件，放宽限制：匹配当前类型、通用类型、旧的 FILE 类型、或空类型
                query.and(wq -> wq.eq(SystemResource::getType, type)
                        .or().eq(SystemResource::getType, "COMMON")
                        .or().eq(SystemResource::getType, "FILE")
                        .or().eq(SystemResource::getType, "")
                        .or().isNull(SystemResource::getType));
            } else {
                // 对于程序路径，依然要求类型匹配或为通用
                query.and(wq -> wq.eq(SystemResource::getType, type).or().eq(SystemResource::getType, "COMMON"));
            }
        }
        if (category != null) query.eq(SystemResource::getCategory, category);
        if (availableOnly != null && availableOnly) query.eq(SystemResource::getIsUsed, 0);
        return SaResult.data(resourceMapper.selectList(query));
    }

    @PostMapping("/resources")
    public SaResult saveResource(@RequestBody SystemResource resource) {
        // JAR 类型资源校验：path 必须以 .jar 结尾
        if ("JAR".equals(resource.getType())) {
            if (StrUtil.isBlank(resource.getPath()) || !resource.getPath().endsWith(".jar")) {
                return SaResult.error("SpringBoot 类型路径必须以 .jar 结尾");
            }
        }
        if (resource.getId() == null) {
            resourceMapper.insert(resource);
        } else {
            resourceMapper.updateById(resource);
        }
        return SaResult.ok();
    }

    @DeleteMapping("/resources/{id}")
    public SaResult deleteResource(@PathVariable Integer id) {
        resourceMapper.deleteById(id);
        return SaResult.ok();
    }

    // --- JDK Management ---

    @GetMapping("/jdks")
    public SaResult getJdks() {
        return SaResult.data(jdkMapper.selectList(null));
    }

    @PostMapping("/jdks")
    public SaResult saveJdk(@RequestBody SystemJdk jdk) {
        if (jdk.getId() == null) {
            jdkMapper.insert(jdk);
        } else {
            jdkMapper.updateById(jdk);
        }
        return SaResult.ok();
    }

    @DeleteMapping("/jdks/{id}")
    public SaResult deleteJdk(@PathVariable Integer id) {
        jdkMapper.deleteById(id);
        return SaResult.ok();
    }

    @GetMapping("/resources/file-content")
    public SaResult getFileContent(@RequestParam String path) {
        File file = new File(path);
        if (!file.exists()) return SaResult.error("文件不存在");
        return SaResult.data(FileUtil.readUtf8String(file));
    }

    @PostMapping("/resources/file-content")
    public SaResult saveFileContent(@RequestBody Map<String, String> data) {
        String path = data.get("path");
        String content = data.get("content");
        File file = new File(path);
        FileUtil.writeUtf8String(content, file);
        return SaResult.ok("文件已更新");
    }

    @GetMapping("/system-stats")
    public SaResult getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 1. CPU & Memory 
        // Note: Using com.sun.management.OperatingSystemMXBean for more accurate stats
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        
        double systemCpuLoad = osBean.getCpuLoad(); // In Java 17+, getSystemCpuLoad is deprecated or not available in standard MXBean
        if (systemCpuLoad < 0) systemCpuLoad = 0; 
        stats.put("cpu", Math.round(systemCpuLoad * 100));

        long totalMem = osBean.getTotalMemorySize();
        long freeMem = osBean.getFreeMemorySize();
        double memUsage = (double) (totalMem - freeMem) / totalMem;
        stats.put("memory", Math.round(memUsage * 100));

        // 2. Disk Usage
        File root = new File("/");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getFreeSpace();
        if (totalSpace > 0) {
            double diskUsage = (double) (totalSpace - freeSpace) / totalSpace;
            stats.put("disk", Math.round(diskUsage * 100));
        } else {
            stats.put("disk", 0);
        }

        return SaResult.data(stats);
    }
}
