package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.what.deploy.entity.AppBackup;
import com.what.deploy.mapper.AppBackupMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backups")
public class AppBackupController {

    @Autowired
    private AppBackupMapper backupMapper;

    @GetMapping("/{appId}")
    public SaResult getBackups(@PathVariable Integer appId) {
        return SaResult.data(backupMapper.selectList(new LambdaQueryWrapper<AppBackup>()
                .eq(AppBackup::getAppId, appId)
                .orderByDesc(AppBackup::getCreateTime)));
    }

    @DeleteMapping("/{id}")
    public SaResult delete(@PathVariable Integer id) {
        AppBackup backup = backupMapper.selectById(id);
        if (backup != null) {
            cn.hutool.core.io.FileUtil.del(backup.getFilePath());
            backupMapper.deleteById(id);
        }
        return SaResult.ok();
    }
}
