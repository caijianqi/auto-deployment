package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.what.deploy.entity.DeployLog;
import com.what.deploy.mapper.DeployLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
public class DeployLogController {

    @Autowired
    private DeployLogMapper logMapper;

    @GetMapping("/all/latest")
    public SaResult listLatest() {
        return SaResult.data(logMapper.selectList(new LambdaQueryWrapper<DeployLog>()
                .orderByDesc(DeployLog::getCreateTime)
                .last("LIMIT 10")));
    }

    @GetMapping("/all/list")
    public SaResult listAll(@RequestParam(defaultValue = "1") Integer current,
                           @RequestParam(defaultValue = "10") Integer size) {
        return SaResult.data(logMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size),
                new LambdaQueryWrapper<DeployLog>().orderByDesc(DeployLog::getCreateTime)
        ));
    }

    @GetMapping("/app/{appId}")
    public SaResult getLogs(@PathVariable Integer appId) {
        return SaResult.data(logMapper.selectList(new LambdaQueryWrapper<DeployLog>()
                .eq(DeployLog::getAppId, appId)
                .orderByDesc(DeployLog::getCreateTime)));
    }
}
