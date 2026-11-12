package com.what.deploy.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
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

            // 1. 登录状态检查：仅放行登录接口与 WebSocket，其余接口均需已登录
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login", "/api/ws/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**").excludePathPatterns("/api/ws/**");
    }
}
