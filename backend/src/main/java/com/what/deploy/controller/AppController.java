package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import com.what.deploy.entity.AppInfo;
import com.what.deploy.service.AppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/apps")
public class AppController {

    @Autowired
    private AppService appService;

    @GetMapping
    public SaResult list() {
        return SaResult.data(appService.list());
    }

    @PostMapping
    public SaResult save(@RequestBody AppInfo app) {
        appService.saveOrUpdate(app);
        return SaResult.data(app);
    }

    @DeleteMapping("/{id}")
    public SaResult delete(@PathVariable Integer id) {
        appService.removeById(id);
        return SaResult.ok();
    }

    @PostMapping("/{id}/start")
    public SaResult start(@PathVariable Integer id) throws IOException {
        appService.start(id);
        return SaResult.ok();
    }

    @PostMapping("/{id}/stop")
    public SaResult stop(@PathVariable Integer id) {
        appService.stop(id);
        return SaResult.ok();
    }

    @PostMapping("/{id}/deploy")
    public SaResult deploy(@PathVariable Integer id, @RequestParam("file") MultipartFile file) throws IOException {
        appService.deploy(id, file);
        return SaResult.ok();
    }

    @PostMapping("/{id}/rollback")
    public SaResult rollback(@PathVariable Integer id, @RequestParam("backupId") Integer backupId) throws IOException {
        appService.rollback(id, backupId);
        return SaResult.ok();
    }
}
