package com.what.deploy.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 0. WebSocket 豁免（多重保险）
            if (SaRouter.match("/api/ws/**").isHit) {
                return;
            }

            // 1. 系统初始化检查
            if (!InitConfig.initialized) {
                SaRouter.match("/api/**")
                        .notMatch("/api/auth/status", "/api/auth/init")
                        .back(SaResult.error("系统未初始化").set("code", 4016));
            }

            // 2. 登录状态检查
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login", "/api/auth/status", "/api/auth/init", "/api/ws/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**").excludePathPatterns("/api/ws/**");
    }
}
