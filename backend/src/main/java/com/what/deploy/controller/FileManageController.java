package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.what.deploy.model.FileItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 服务器文件管理接口
 * 支持完整的文件与目录 CRUD、上传、下载、在线编辑保存、重命名、解压缩等操作
 */
@RestController
@RequestMapping("/api/file")
@Slf4j
public class FileManageController {

    /**
     * 获取系统根目录（Windows 下为各盘符，Linux 下为 /）
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
                    // 文件夹在前，然后按名称不区分大小写排序
                    if (f1.getIsDir() != f2.getIsDir()) {
                        return f2.getIsDir() ? 1 : -1;
                    }
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .collect(Collectors.toList());

        return SaResult.data(result);
    }

    /**
     * 读取文件内容 (预览/编辑)
     */
    @GetMapping("/content")
    public SaResult getFileContent(@RequestParam String path) {
        File file = new File(path);
        if (!file.exists()) return SaResult.error("文件不存在");
        if (file.isDirectory()) return SaResult.error("不能预览文件夹");

        // 限制在线编辑/预览大小（提高至 10MB）
        if (file.length() > 10 * 1024 * 1024) {
            return SaResult.error("文件过大（超过 10MB），暂不支持在线编辑预览");
        }

        try {
            return SaResult.data(FileUtil.readUtf8String(file));
        } catch (Exception e) {
            log.error("Failed to read file: {}", path, e);
            return SaResult.error("读取失败: " + e.getMessage());
        }
    }

    /**
     * 保存编辑后的文本内容
     */
    @PostMapping("/save-content")
    public SaResult saveFileContent(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        String content = body.get("content");
        if (StrUtil.isBlank(path)) {
            return SaResult.error("文件路径不能为空");
        }
        File file = new File(path);
        if (file.isDirectory()) {
            return SaResult.error("目标路径是文件夹，无法写入内容");
        }

        try {
            if (!file.exists()) {
                FileUtil.touch(file);
            }
            FileUtil.writeUtf8String(content == null ? "" : content, file);
            log.info("File saved successfully: {}", path);
            return SaResult.ok("保存成功");
        } catch (Exception e) {
            log.error("Failed to save file: {}", path, e);
            return SaResult.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 创建新文件夹
     */
    @PostMapping("/create-folder")
    public SaResult createFolder(@RequestBody Map<String, String> body) {
        String parentPath = body.get("path");
        String folderName = body.get("folderName");
        if (StrUtil.isBlank(parentPath) || StrUtil.isBlank(folderName)) {
            return SaResult.error("路径和文件夹名称不能为空");
        }

        File target = new File(parentPath, folderName.trim());
        if (target.exists()) {
            return SaResult.error("该文件夹或同名文件已存在");
        }

        try {
            FileUtil.mkdir(target);
            log.info("Created folder: {}", target.getAbsolutePath());
            return SaResult.ok("文件夹创建成功");
        } catch (Exception e) {
            log.error("Failed to create folder: {}", target.getAbsolutePath(), e);
            return SaResult.error("创建文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 创建新空白文件
     */
    @PostMapping("/create-file")
    public SaResult createFile(@RequestBody Map<String, String> body) {
        String parentPath = body.get("path");
        String fileName = body.get("fileName");
        String content = body.get("content");
        if (StrUtil.isBlank(parentPath) || StrUtil.isBlank(fileName)) {
            return SaResult.error("路径和文件名称不能为空");
        }

        File target = new File(parentPath, fileName.trim());
        if (target.exists()) {
            return SaResult.error("同名文件或文件夹已存在");
        }

        try {
            FileUtil.touch(target);
            if (StrUtil.isNotEmpty(content)) {
                FileUtil.writeUtf8String(content, target);
            }
            log.info("Created file: {}", target.getAbsolutePath());
            return SaResult.ok("文件创建成功");
        } catch (Exception e) {
            log.error("Failed to create file: {}", target.getAbsolutePath(), e);
            return SaResult.error("创建文件失败: " + e.getMessage());
        }
    }

    /**
     * 文件上传 (支持批量或单文件上传至指定目录)
     */
    @PostMapping("/upload")
    public SaResult uploadFile(@RequestParam("file") MultipartFile file,
                               @RequestParam("path") String path,
                               @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        if (file == null || file.isEmpty()) {
            return SaResult.error("上传文件不能为空");
        }
        if (StrUtil.isBlank(path)) {
            return SaResult.error("目标目录不能为空");
        }

        File targetDir = new File(path);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return SaResult.error("目标目录不存在: " + path);
        }

        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            originalFilename = "upload_" + System.currentTimeMillis();
        }

        File destFile = new File(targetDir, originalFilename);
        if (destFile.exists() && !overwrite) {
            return SaResult.error("文件 " + originalFilename + " 已存在，请选择覆盖或重命名后再上传").setCode(409);
        }

        try {
            file.transferTo(destFile);
            log.info("Uploaded file to: {}", destFile.getAbsolutePath());
            return SaResult.ok("文件上传成功");
        } catch (Exception e) {
            log.error("Failed to upload file: {}", destFile.getAbsolutePath(), e);
            return SaResult.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 文件或文件夹重命名
     */
    @PostMapping("/rename")
    public SaResult rename(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        String newName = body.get("newName");
        if (StrUtil.isBlank(path) || StrUtil.isBlank(newName)) {
            return SaResult.error("路径和新名称不能为空");
        }

        File src = new File(path);
        if (!src.exists()) {
            return SaResult.error("源文件或目录不存在");
        }

        try {
            File renamed = FileUtil.rename(src, newName.trim(), false);
            log.info("Renamed: {} -> {}", src.getAbsolutePath(), renamed.getAbsolutePath());
            return SaResult.ok("重命名成功");
        } catch (Exception e) {
            log.error("Failed to rename: {}", path, e);
            return SaResult.error("重命名失败: " + e.getMessage());
        }
    }

    /**
     * 删除单个文件或文件夹
     */
    @PostMapping("/delete")
    public SaResult delete(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (StrUtil.isBlank(path)) {
            return SaResult.error("路径不能为空");
        }

        File target = new File(path);
        if (!target.exists()) {
            return SaResult.ok("目标已不存在");
        }

        // 安全防护：防止根目录被删除
        if (isRootPath(target)) {
            return SaResult.error("受保护的系统根目录禁止删除");
        }

        try {
            boolean success = FileUtil.del(target);
            if (success) {
                log.info("Deleted: {}", path);
                return SaResult.ok("删除成功");
            } else {
                return SaResult.error("删除失败，可能文件被其他进程占用");
            }
        } catch (Exception e) {
            log.error("Failed to delete: {}", path, e);
            return SaResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文件或文件夹
     */
    @PostMapping("/batch-delete")
    public SaResult batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> paths = body.get("paths");
        if (paths == null || paths.isEmpty()) {
            return SaResult.error("请选择要删除的项目");
        }

        int successCount = 0;
        List<String> failPaths = new ArrayList<>();

        for (String p : paths) {
            if (StrUtil.isBlank(p)) continue;
            File f = new File(p);
            if (!f.exists()) {
                successCount++;
                continue;
            }
            if (isRootPath(f)) {
                failPaths.add(p + " (系统根目录)");
                continue;
            }
            try {
                if (FileUtil.del(f)) {
                    successCount++;
                } else {
                    failPaths.add(p);
                }
            } catch (Exception e) {
                failPaths.add(p);
            }
        }

        if (failPaths.isEmpty()) {
            return SaResult.ok(String.format("成功删除 %d 项", successCount));
        } else {
            return SaResult.ok(String.format("成功删除 %d 项，%d 项删除失败: %s", successCount, failPaths.size(), String.join(", ", failPaths)));
        }
    }

    /**
     * 解压 ZIP 归档文件
     */
    @PostMapping("/unzip")
    public SaResult unzipFile(@RequestBody Map<String, String> body) {
        String zipPath = body.get("path");
        String targetDir = body.get("targetDir");

        if (StrUtil.isBlank(zipPath)) {
            return SaResult.error("压缩包路径不能为空");
        }

        File zipFile = new File(zipPath);
        if (!zipFile.exists() || zipFile.isDirectory()) {
            return SaResult.error("压缩包不存在");
        }

        File outDir;
        if (StrUtil.isNotBlank(targetDir)) {
            outDir = new File(targetDir);
        } else {
            outDir = zipFile.getParentFile();
        }

        if (!outDir.exists()) {
            FileUtil.mkdir(outDir);
        }

        try {
            // 默认 GBK / UTF-8 自适应解压
            try {
                ZipUtil.unzip(zipFile, outDir, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 兼容 Windows 下 GBK 编码创建的 zip 文件
                ZipUtil.unzip(zipFile, outDir, java.nio.charset.Charset.forName("GBK"));
            }
            log.info("Unzipped: {} -> {}", zipPath, outDir.getAbsolutePath());
            return SaResult.ok("解压成功");
        } catch (Exception e) {
            log.error("Failed to unzip: {}", zipPath, e);
            return SaResult.error("解压失败: " + e.getMessage());
        }
    }

    /**
     * 压缩选定文件或文件夹为 ZIP
     */
    @PostMapping("/zip")
    public SaResult zipFiles(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> srcPaths = (List<String>) body.get("paths");
        String targetZipPath = (String) body.get("targetZipPath");

        if (srcPaths == null || srcPaths.isEmpty()) {
            return SaResult.error("请选择要压缩的文件或文件夹");
        }

        File zipFile;
        if (StrUtil.isNotBlank(targetZipPath)) {
            zipFile = new File(targetZipPath);
        } else {
            File first = new File(srcPaths.get(0));
            zipFile = new File(first.getParentFile(), first.getName() + ".zip");
        }

        try {
            File[] srcFiles = srcPaths.stream().map(File::new).filter(File::exists).toArray(File[]::new);
            if (srcFiles.length == 0) {
                return SaResult.error("所选源文件均不存在");
            }

            ZipUtil.zip(zipFile, false, srcFiles);
            log.info("Created zip: {}", zipFile.getAbsolutePath());
            return SaResult.ok("压缩成功: " + zipFile.getName());
        } catch (Exception e) {
            log.error("Failed to create zip: {}", zipFile.getAbsolutePath(), e);
            return SaResult.error("压缩失败: " + e.getMessage());
        }
    }

    /**
     * 下载或读取文件流 (用于下载 / 图片预览)
     */
    @GetMapping("/download")
    public void download(@RequestParam String path, jakarta.servlet.http.HttpServletResponse response) {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            return;
        }

        String mimeType = FileUtil.getMimeType(file.getName());
        if (StrUtil.isNotBlank(mimeType)) {
            response.setContentType(mimeType);
        } else {
            response.setContentType("application/octet-stream");
        }

        // 设置附件下载头部
        try {
            String encodedName = java.net.URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
            response.setHeader("Content-Length", String.valueOf(file.length()));
            FileUtil.writeToStream(file, response.getOutputStream());
        } catch (Exception e) {
            log.error("Download failed for path: {}", path, e);
        }
    }

    private boolean isRootPath(File file) {
        if (file == null) return false;
        String abs = file.getAbsolutePath();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File r : roots) {
                if (r.getAbsolutePath().equalsIgnoreCase(abs) || (r.getAbsolutePath() + "\\").equalsIgnoreCase(abs) || (r.getAbsolutePath() + "/").equalsIgnoreCase(abs)) {
                    return true;
                }
            }
        }
        return "/".equals(abs) || "\\".equals(abs);
    }
}
