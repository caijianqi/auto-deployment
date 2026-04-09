package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.what.deploy.model.FileItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务器文件管理接口
 */
@RestController
@RequestMapping("/api/file")
@Slf4j
public class FileManageController {

    /**
     * 获取系统根目录（Windows 下为盘符，Linux 下为 /）
     */
    @GetMapping("/roots")
    public SaResult getRoots() {
        File[] roots = File.listRoots();
        List<FileItem> result = new ArrayList<>();
        if (roots != null) {
            for (File root : roots) {
                result.add(FileItem.builder()
                        .name(root.getAbsolutePath())
                        .path(root.getAbsolutePath())
                        .isDir(true)
                        .size(root.getTotalSpace())
                        .build());
            }
        }
        return SaResult.data(result);
    }

    /**
     * 获取指定目录下的文件列表
     */
    @GetMapping("/list")
    public SaResult listFiles(@RequestParam String path) {
        if (StrUtil.isBlank(path)) {
            return getRoots();
        }
        File dir = new File(path);
        if (!dir.exists()) {
            return SaResult.error("目录不存在: " + path);
        }
        if (!dir.isDirectory()) {
            return SaResult.error("不是一个目录: " + path);
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return SaResult.data(new ArrayList<>());
        }

        List<FileItem> result = Arrays.stream(files)
                .map(f -> FileItem.builder()
                        .name(f.getName())
                        .path(f.getAbsolutePath())
                        .isDir(f.isDirectory())
                        .size(f.isDirectory() ? null : f.length())
                        .lastModified(new Date(f.lastModified()))
                        .build())
                .sorted((f1, f2) -> {
                    // 文件夹在前，然后按名称排序
                    if (f1.getIsDir() != f2.getIsDir()) {
                        return f2.getIsDir() ? 1 : -1;
                    }
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .collect(Collectors.toList());

        return SaResult.data(result);
    }

    /**
     * 读取文件内容 (预览)
     */
    @GetMapping("/content")
    public SaResult getFileContent(@RequestParam String path) {
        File file = new File(path);
        if (!file.exists()) return SaResult.error("文件不存在");
        if (file.isDirectory()) return SaResult.error("不能预览文件夹");
        
        // 限制预览大小，避免内存溢出 (例如超过 2MB 的不直接文本预览)
        if (file.length() > 2 * 1024 * 1024) {
             return SaResult.error("文件过大（超过 2MB），暂不支持在线预览");
        }
        
        try {
            return SaResult.data(FileUtil.readUtf8String(file));
        } catch (Exception e) {
            log.error("Failed to read file: {}", path, e);
            return SaResult.error("读取失败，可能存在编码冲突: " + e.getMessage());
        }
    }

    /**
     * 下载或读取文件流 (用于图片/资源预览)
     */
    @GetMapping("/download")
    public void download(@RequestParam String path, jakarta.servlet.http.HttpServletResponse response) {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            return;
        }
        
        // 设置响应类型 (可选)
        String mimeType = FileUtil.getMimeType(file.getName());
        if (StrUtil.isNotBlank(mimeType)) {
            response.setContentType(mimeType);
        }
        
        try {
            FileUtil.writeToStream(file, response.getOutputStream());
        } catch (Exception e) {
            log.error("Download failed for path: {}", path, e);
        }
    }
}
