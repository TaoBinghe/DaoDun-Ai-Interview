package com.daodun.common;

import lombok.Getter;

/**
 * 业务异常，用于在 Service 层抛出可预期的业务错误
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
