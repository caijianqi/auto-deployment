package com.what.deploy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.what.deploy.entity.NginxBackup;

public interface NginxBackupService extends IService<NginxBackup> {
    void createBackup(String configPath);
    void restoreBackup(Integer id);
    void deleteBackup(Integer id);
    String getBackupContent(Integer id);
}
