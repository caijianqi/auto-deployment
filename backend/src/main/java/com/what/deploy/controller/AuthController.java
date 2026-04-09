package com.what.deploy.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.what.deploy.entity.SystemResource;
import com.what.deploy.entity.User;
import com.what.deploy.mapper.SystemResourceMapper;
import com.what.deploy.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemResourceMapper systemResourceMapper;

    @GetMapping("/status")
    public SaResult getStatus() {
        Long count = userMapper.selectCount(null);
        return SaResult.data(count > 0);
    }

    @PostMapping("/init")
    public SaResult init(@RequestBody User user) {
        Long count = userMapper.selectCount(null);
        if (count > 0) {
            return SaResult.error("系统已初始化，禁止重复操作");
        }
        user.setNickname("Administrator");
        user.setCreateTime(new java.util.Date());
        // 密码加密存储
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        userMapper.insert(user);

        // 初始化预置资源
        insertDefaultResources();

        com.what.deploy.config.InitConfig.initialized = true;
        return SaResult.ok("初始化成功，请登录");
    }

    private void insertDefaultResources() {
        SystemResource r1 = new SystemResource();
        r1.setPath("/data/criminal/jeeplus-vue.jar");
        r1.setType("JAR");
        r1.setCategory("APP");
        r1.setIsUsed(0);
        r1.setRemark("刑侦后端");
        systemResourceMapper.insert(r1);

        SystemResource r2 = new SystemResource();
        r2.setPath("/data/criminal/application.yml");
        r2.setType("FILE");
        r2.setCategory("CONFIG");
        r2.setIsUsed(0);
        r2.setRemark("刑侦后端配置");
        systemResourceMapper.insert(r2);

        SystemResource r3 = new SystemResource();
        r3.setPath("/usr/share/nginx/html/criminal");
        r3.setType("VUE");
        r3.setCategory("APP");
        r3.setIsUsed(0);
        r3.setRemark("刑侦前端");
        systemResourceMapper.insert(r3);
    }

    @PostMapping("/login")
    public SaResult login(@RequestBody User user) {
        User dbUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, user.getUsername()));
        
        if (dbUser != null && BCrypt.checkpw(user.getPassword(), dbUser.getPassword())) {
            StpUtil.login(dbUser.getId());
            return SaResult.data(StpUtil.getTokenInfo());
        }
        return SaResult.error("Invalid username or password");
    }

    @PostMapping("/logout")
    public SaResult logout() {
        StpUtil.logout();
        return SaResult.ok();
    }

    @PutMapping("/password")
    public SaResult updatePassword(@RequestBody java.util.Map<String, String> params) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        if (cn.hutool.core.util.StrUtil.hasBlank(oldPassword, newPassword)) {
            return SaResult.error("密码不能为空");
        }

        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);

        if (user == null) {
            return SaResult.error("用户不存在");
        }

        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            return SaResult.error("原密码错误");
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);

        return SaResult.ok("密码修改成功");
    }
}
