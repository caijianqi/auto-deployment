package com.what.deploy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.what.deploy.entity.AppInfo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface AppService extends IService<AppInfo> {
    void start(Integer id) throws IOException;

    void stop(Integer id);

    void deploy(Integer id, MultipartFile file) throws IOException;

    void rollback(Integer id, Integer backupId) throws IOException;
}
