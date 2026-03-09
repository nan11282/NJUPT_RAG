package com.njupt.rag.exception;

import lombok.Getter;

/**
 * 业务异常类。
 * <p>
 * 用于处理业务逻辑中的预期异常，如参数校验失败、业务规则不满足等。
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 创建业务异常。
     *
     * @param message 错误信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 创建业务异常（指定错误码）。
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建业务异常（带原因）。
     *
     * @param message 错误信息
     * @param cause   原因异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 400;
    }

    /**
     * 创建业务异常（带原因和错误码）。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
