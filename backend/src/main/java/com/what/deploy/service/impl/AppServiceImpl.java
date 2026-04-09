package com.what.deploy.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.what.deploy.entity.AppBackup;
import com.what.deploy.entity.AppInfo;
import com.what.deploy.entity.DeployLog;
import com.what.deploy.entity.SystemResource;
import com.what.deploy.mapper.AppBackupMapper;
import com.what.deploy.mapper.AppInfoMapper;
import com.what.deploy.mapper.DeployLogMapper;
import com.what.deploy.mapper.SystemResourceMapper;
import com.what.deploy.service.AppService;
import com.what.deploy.service.strategy.AppDeployStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppInfoMapper, AppInfo> implements AppService {

    @Value("${deploy.path.backups}")
    private String backupsPath;

    @Autowired
    private AppBackupMapper backupMapper;

    @Autowired
    private DeployLogMapper logMapper;

    @Autowired
    private SystemResourceMapper resMapper;

    @Autowired
    private List<AppDeployStrategy> strategies;

    private AppDeployStrategy getStrategy(String type) {
        return strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的应用类型: " + type));
    }

    @Override
    public List<AppInfo> list() {
        List<AppInfo> apps = super.list();
        for (AppInfo app : apps) {
            Long oldPid = app.getPid();
            AppDeployStrategy strategy = getStrategy(app.getType());
            
            // isAlive 内部可能会触发基于路径的自动发现并更新 app 的 PID
            if (strategy.isAlive(app)) {
                app.setStatus("RUNNING");
                // 如果数据库记录的 PID 与实际运行的 PID 不一致，则同步更新
                if (oldPid == null || !oldPid.equals(app.getPid())) {
                    log.info("应用 {} PID 从 {} 同步更新为 {}", app.getName(), oldPid, app.getPid());
                    super.updateById(app);
                }
            } else {
                app.setStatus("STOPPED");
                // 如果数据库记录了 PID 但实际未运行，清除过期的 PID
                if (oldPid != null) {
                    app.setPid(null);
                    super.updateById(app);
                }
            }
            
            // 获取实际端口（此时 app.pid 已经是最新且准确的）
            app.setActualPort(strategy.getActualPort(app));
        }
        return apps;
    }

    @Override
    public void start(Integer id) {
        AppInfo app = getById(id);
        if (app == null) return;
        
        // 强制锁定：运行中禁止部署
        checkNotActive(app);

        AppDeployStrategy strategy = getStrategy(app.getType());
        strategy.start(app);
        updateById(app);
        recordLog(id, "START", "Application started. Status: " + app.getStatus());
    }

    @Override
    public void stop(Integer id) {
        AppInfo app = getById(id);
        if (app == null) return;
        AppDeployStrategy strategy = getStrategy(app.getType());
        strategy.stop(app);
        updateById(app);
        recordLog(id, "STOP", "Application stopped");
    }

    @Override
    public void deploy(Integer id, MultipartFile file) throws IOException {
        AppInfo app = getById(id);
        if (app == null) return;
        
        // 强制锁定：运行中禁止部署
        checkNotActive(app);

        AppDeployStrategy strategy = getStrategy(app.getType());

        // 2. Strategy deployment
        strategy.deploy(app, file);

        // 3. Capture CURRENT version as backup
        log.info("Capturing current version in backups...");
        AppBackup currentVer = backup(app);
        if (currentVer != null) {
            app.setVersion(currentVer.getVersion());
        }

        // 4. Update metadata and record log
        app.setLastDeployTime(new Date());
        updateById(app);
        recordLog(id, "DEPLOY", "应用已更新，文件名：" + file.getOriginalFilename());
    }

    @Override
    @Transactional
    public void rollback(Integer id, Integer backupId) throws IOException {
        AppInfo app = getById(id);
        AppBackup backup = backupMapper.selectById(backupId);
        if (app == null || backup == null) return;
        
        // 强制锁定：运行中禁止回滚
        checkNotActive(app);

        AppDeployStrategy strategy = getStrategy(app.getType());

        // 2. Physical reversion
        strategy.rollback(app, backup.getFilePath());

        app.setVersion(backup.getVersion());
        updateById(app);
        recordLog(id, "ROLLBACK", "回滚至版本: " + backup.getVersion());
    }

    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        AppInfo app = getById(id);
        if (app == null) return false;

        // 强制锁定：运行中禁止删除
        checkNotActive(app);

        Integer appId = (Integer) id;

        // 2. Cascade cleanup database records
        backupMapper.delete(new LambdaQueryWrapper<AppBackup>().eq(AppBackup::getAppId, appId));
        logMapper.delete(new LambdaQueryWrapper<DeployLog>().eq(DeployLog::getAppId, appId));

        // 3. Physical cleanup
        File appDir = new File(app.getPath());
        if (appDir.exists()) {
            log.info("Deleting application path: {}", app.getPath());
            FileUtil.del(appDir);
        }

        File backupDir = new File(backupsPath, app.getName());
        if (backupDir.exists()) {
            log.info("Deleting backup directory: {}", backupDir.getAbsolutePath());
            FileUtil.del(backupDir);
        }

        // 4. Release system resource
        updateResourceStatus(app.getPath(), 0);
        if (StrUtil.isNotBlank(app.getConfigPath())) {
            updateResourceStatus(app.getConfigPath(), 0);
        }

        return super.removeById(id);
    }

    private void updateResourceStatus(String path, int isUsed) {
        if (StrUtil.isBlank(path)) return;
        LambdaQueryWrapper<SystemResource> query = new LambdaQueryWrapper<SystemResource>()
                .eq(SystemResource::getPath, path);
        SystemResource res = resMapper.selectOne(query);
        if (res != null) {
            res.setIsUsed(isUsed);
            resMapper.updateById(res);
            log.info("Resource status updated: {} -> used={}", path, isUsed);
        }
    }

    private AppBackup backup(AppInfo app) {
        File source = new File(app.getPath());
        if (!source.exists() || (source.isDirectory() && FileUtil.isDirEmpty(source))) return null;

        String version = StrUtil.format("v{}", System.currentTimeMillis());
        String backupPath = backupsPath + "/" + app.getName() + "/" + version;

        File dest;
        if (source.isDirectory()) {
            backupPath += ".zip";
            dest = new File(backupPath);
            cn.hutool.core.util.ZipUtil.zip(dest, false, source);
        } else {
            dest = new File(backupPath);
            FileUtil.copy(source, dest, true);
        }

        AppBackup backup = new AppBackup();
        backup.setAppId(app.getId());
        backup.setVersion(version);
        backup.setFilePath(dest.getAbsolutePath());
        backup.setSize(dest.length());
        backup.setCreateTime(new Date());
        backupMapper.insert(backup);
        return backup;
    }

    private void recordLog(Integer appId, String opType, String content) {
        DeployLog deployLog = new DeployLog();
        deployLog.setAppId(appId);
        deployLog.setOpType(opType);
        deployLog.setContent(content);
        deployLog.setCreateTime(new Date());
        logMapper.insert(deployLog);
    }

    private void checkNotActive(AppInfo app) {
        if ("VUE".equals(app.getType())) {
            return;
        }
        AppDeployStrategy strategy = getStrategy(app.getType());
        if ("RUNNING".equals(app.getStatus()) || "STARTING".equals(app.getStatus()) || strategy.isAlive(app)) {
            throw new RuntimeException("应用正在运行或启动中，请先停止后再进行此操作。");
        }
    }

    @Override
    public boolean updateById(AppInfo entity) {
        if (entity.getId() != null) {
            AppInfo existing = getById(entity.getId());
            if (existing != null) {
                checkNotActive(existing);
            }
        }
        return super.updateById(entity);
    }

    @Override
    public boolean saveOrUpdate(AppInfo app) {
        // JAR 类型应用校验：path 必须以 .jar 结尾
        if ("JAR".equals(app.getType())) {
            if (StrUtil.isBlank(app.getPath()) || !app.getPath().endsWith(".jar")) {
                throw new RuntimeException("JAR 应用部署路径必须以 .jar 结尾");
            }
        }
        return super.saveOrUpdate(app);
    }
}
