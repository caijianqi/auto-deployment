package com.what.deploy.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.what.deploy.entity.User;
import com.what.deploy.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

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
