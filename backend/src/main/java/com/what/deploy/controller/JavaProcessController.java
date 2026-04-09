package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import com.what.deploy.service.JavaProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/processes")
public class JavaProcessController {

    @Autowired
    private JavaProcessService javaProcessService;

    @GetMapping
    public SaResult list() {
        return SaResult.data(javaProcessService.listProcesses());
    }

    @PostMapping("/{pid}/kill")
    public SaResult kill(@PathVariable String pid) {
        try {
            javaProcessService.killProcess(pid);
            return SaResult.ok("进程已终止");
        } catch (Exception e) {
            return SaResult.error(e.getMessage());
        }
    }
}
