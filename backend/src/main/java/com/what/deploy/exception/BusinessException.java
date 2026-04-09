package com.what.deploy.exception;

/**
 * 业务异常类，用于向上抛出可预知的业务逻辑错误
 */
public class BusinessException extends RuntimeException {
    private int code = 500;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
