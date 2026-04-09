package com.what.deploy.service.strategy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import com.what.deploy.entity.AppInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * VUE 应用部署策略（静态资源部署）
 */
@Component
@Slf4j
public class VueDeployStrategy implements AppDeployStrategy {

    @Override
    public boolean supports(String type) {
        return "VUE".equals(type);
    }

    @Override
    public void start(AppInfo app) {
        // VUE 应用通常由 Nginx 托管，无需独立启动进程
        log.info("VUE application [{}] handled by Nginx. Start ignored.", app.getName());
    }

    @Override
    public void stop(AppInfo app) {
        // VUE 应用无需独立停止进程
        log.info("VUE application [{}] handled by Nginx. Stop ignored.", app.getName());
    }

    @Override
    public void deploy(AppInfo app, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.endsWith(".zip")) {
            throw new RuntimeException("VUE 部署仅支持 .zip 格式的压缩包");
        }

        File destDir = new File(app.getPath());
        if (!destDir.exists()) FileUtil.mkdir(destDir);

        // 创建临时文件进行解压处理
        File tempZip = File.createTempFile("vue_deploy_", "_" + fileName);
        if (tempZip == null) {
            throw new IOException("无法创建临时文件进行部署");
        }
        try {
            file.transferTo(tempZip);
            handleZipDeployment(tempZip, destDir);
        } finally {
            FileUtil.del(tempZip);
        }
    }

    @Override
    public void rollback(AppInfo app, String backupFilePath) throws IOException {
        File source = new File(backupFilePath);
        File dest = new File(app.getPath());
        
        if (backupFilePath.endsWith(".zip")) {
            FileUtil.clean(dest);
            ZipUtil.unzip(source, dest);
        } else {
            // 兼容性处理
            if (dest.exists() && dest.isDirectory()) {
                FileUtil.clean(dest);
                FileUtil.copyContent(source, dest, true);
            } else {
                FileUtil.copy(source, dest, true);
            }
        }
    }

    @Override
    public boolean isAlive(AppInfo app) {
        // VUE 应用静态资源始终为“在线”状态（只要文件存在）
        return new File(app.getPath()).exists();
    }

    private void handleZipDeployment(File zipFile, File destDir) throws IOException {
        List<String> names;
        try (ZipFile zf = new ZipFile(zipFile)) {
            names = ZipUtil.listFileNames(zf, "UTF-8");
        }

        // 检测是否存在单一根目录（Lifting）
        Set<String> topDirs = new HashSet<>();
        for (String name : names) {
            int idx = name.indexOf('/');
            if (idx != -1) {
                topDirs.add(name.substring(0, idx + 1));
            } else {
                topDirs.add("");
            }
        }

        if (topDirs.size() == 1 && !topDirs.contains("")) {
            String rootDir = topDirs.iterator().next();
            log.info("Detected single root folder [{}] in ZIP. Performing lifting...", rootDir);
            File tempExtract = new File(destDir.getParent(), "vue_ext_" + System.currentTimeMillis());
            try {
                ZipUtil.unzip(zipFile, tempExtract);
                File internalDist = new File(tempExtract, rootDir);
                FileUtil.clean(destDir);
                FileUtil.moveContent(internalDist, destDir, true);
            } finally {
                FileUtil.del(tempExtract);
            }
        } else {
            FileUtil.clean(destDir);
            ZipUtil.unzip(zipFile, destDir);
        }
    }
}
