package com.njupt.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 响应格式。
 * <p>
 * 所有 API 接口都应使用此格式返回数据，保持响应一致性。
 *
 * @param <T> 返回数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 业务状态码
     */
    private int code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 创建成功响应（带数据）。
     *
     * @param data    返回数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return Result 实例
     */
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(true, 200, message, data, System.currentTimeMillis());
    }

    /**
     * 创建成功响应（默认提示信息）。
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return Result 实例
     */
    public static <T> Result<T> success(T data) {
        return success(data, "操作成功");
    }

    /**
     * 创建成功响应（无数据）。
     *
     * @param message 提示信息
     * @param <T>     数据类型
     * @return Result 实例
     */
    public static <T> Result<T> success(String message) {
        return success(null, message);
    }

    /**
     * 创建失败响应。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return Result 实例
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(false, code, message, null, System.currentTimeMillis());
    }

    /**
     * 创建失败响应（使用默认错误码）。
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return Result 实例
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}
