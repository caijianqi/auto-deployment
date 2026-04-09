package com.what.deploy.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import com.what.deploy.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理登录异常 (Sa-Token)
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public SaResult handlerNotLoginException(NotLoginException e) {
        return SaResult.error("未登录或已过期: " + e.getMessage()).setCode(401);
    }

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public SaResult handlerBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return SaResult.error(e.getMessage()).setCode(e.getCode());
    }

    /**
     * 处理其他未捕获的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public SaResult handlerRuntimeException(RuntimeException e) {
        log.error("运行时未知异常: ", e);
        return SaResult.error("系统繁忙，请稍后再试: " + e.getMessage());
    }

    /**
     * 处理 Spring Boot 3.2+ 找不到静态资源或 API 的情况 (404)
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public SaResult handlerNoResourceFoundException(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("资源未找到: {}", e.getMessage());
        return SaResult.error("请求的资源或接口不存在: " + e.getResourcePath()).setCode(404);
    }

    /**
     * 处理顶级异常
     */
    @ExceptionHandler(Exception.class)
    public SaResult handlerException(Exception e) {
        log.error("系统顶级异常: ", e);
        return SaResult.error("服务器内部错误");
    }
}
