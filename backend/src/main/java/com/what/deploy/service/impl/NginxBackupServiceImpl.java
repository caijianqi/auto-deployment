package com.what.deploy.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.what.deploy.entity.NginxBackup;
import com.what.deploy.mapper.NginxBackupMapper;
import com.what.deploy.service.NginxBackupService;
import com.what.deploy.service.NginxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Date;

@Service
public class NginxBackupServiceImpl extends ServiceImpl<NginxBackupMapper, NginxBackup> implements NginxBackupService {

    @Autowired
    @Lazy
    private NginxService nginxService;

    private String getBackupDir() {
        String dir = System.getProperty("user.dir") + File.separator + "nginx_backups";
        if (!FileUtil.exist(dir)) {
            FileUtil.mkdir(dir);
        }
        return dir;
    }

    @Override
    @Transactional
    public void createBackup(String configPath) {
        if (!FileUtil.exist(configPath)) {
            return;
        }

        String fileName = "nginx_" + DateUtil.format(new Date(), "yyyyMMdd_HHmmss") + ".conf";
        String destPath = getBackupDir() + File.separator + fileName;

        // Copy file
        FileUtil.copy(configPath, destPath, true);

        // Save record
        NginxBackup backup = new NginxBackup();
        backup.setFilePath(destPath);
        backup.setSize(new File(destPath).length());
        backup.setRemark("Auto backup before save");
        backup.setCreateTime(new Date());
        this.save(backup);
    }

    @Override
    @Transactional
    public void restoreBackup(Integer id) {
        NginxBackup backup = this.getById(id);
        if (backup == null || !FileUtil.exist(backup.getFilePath())) {
            throw new RuntimeException("Backup file not found");
        }

        String activeConfigPath = nginxService.getNginxHome() + File.separator + "conf" + File.separator + "nginx.conf";
        
        // Restore file
        FileUtil.copy(backup.getFilePath(), activeConfigPath, true);
        
        // Reload Nginx
        nginxService.reload();
    }

    @Override
    @Transactional
    public void deleteBackup(Integer id) {
        NginxBackup backup = this.getById(id);
        if (backup != null) {
            FileUtil.del(backup.getFilePath());
            this.removeById(id);
        }
    }

    @Override
    public String getBackupContent(Integer id) {
        NginxBackup backup = this.getById(id);
        if (backup == null || !FileUtil.exist(backup.getFilePath())) {
            throw new RuntimeException("备份文件不存在");
        }
        return FileUtil.readUtf8String(backup.getFilePath());
    }
}
