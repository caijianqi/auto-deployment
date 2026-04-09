package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import com.what.deploy.entity.NginxBackup;
import com.what.deploy.service.NginxBackupService;
import com.what.deploy.service.NginxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nginx")
public class NginxController {

    @Autowired
    private NginxService nginxService;

    @GetMapping("/status")
    public SaResult getStatus() {
        boolean running = nginxService.isRunning();
        return SaResult.data(running);
    }

    @GetMapping("/home")
    public SaResult getHome() {
        return SaResult.data(nginxService.getNginxHome());
    }

    @PostMapping("/home")
    public SaResult updateHome(@RequestBody Map<String, String> data) {
        String path = data.get("path");
        nginxService.saveNginxHome(path);
        return SaResult.ok("Nginx 路径已更新");
    }

    @PostMapping("/start")
    public SaResult start() {
        return SaResult.ok(nginxService.start());
    }

    @PostMapping("/stop")
    public SaResult stop() {
        return SaResult.ok(nginxService.stop());
    }

    @PostMapping("/restart")
    public SaResult restart() {
        return SaResult.ok(nginxService.reload());
    }

    @GetMapping("/config")
    public SaResult getConfig() {
        return SaResult.data(nginxService.getConfigContent());
    }

    @PostMapping("/config")
    public SaResult saveConfig(@RequestBody Map<String, String> data) {
        String content = data.get("content");
        nginxService.saveConfig(content);
        return SaResult.ok("配置文件已保存并自动备份");
    }

    @Autowired
    private NginxBackupService nginxBackupService;

    @GetMapping("/backups")
    public SaResult listBackups() {
        return SaResult.data(nginxBackupService.list(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NginxBackup>()
                .orderByDesc(NginxBackup::getCreateTime)
        ));
    }

    @PostMapping("/backups/{id}/restore")
    public SaResult restoreBackup(@PathVariable Integer id) {
        nginxBackupService.restoreBackup(id);
        return SaResult.ok("配置已恢复并热重载");
    }

    @DeleteMapping("/backups/{id}")
    public SaResult deleteBackup(@PathVariable Integer id) {
        nginxBackupService.deleteBackup(id);
        return SaResult.ok("备份已删除");
    }

    @GetMapping("/backups/{id}/content")
    public SaResult getBackupContent(@PathVariable Integer id) {
        return SaResult.data(nginxBackupService.getBackupContent(id));
    }

    @PutMapping("/backups/{id}/remark")
    public SaResult updateBackupRemark(@PathVariable Integer id, @RequestBody NginxBackup backup) {
        NginxBackup existing = nginxBackupService.getById(id);
        if (existing == null) {
            return SaResult.error("备份已不存在");
        }
        existing.setRemark(backup.getRemark());
        nginxBackupService.updateById(existing);
        return SaResult.ok("备注已更新");
    }
}
