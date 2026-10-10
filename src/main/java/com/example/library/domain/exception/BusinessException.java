package com.example.library.domain.exception;

/**
 * 业务异常。领域层抛出，表现层用 @RestControllerAdvice 统一捕获并映射为 HTTP 语义。
 * 异常中不携带堆栈给前端，只给中文业务提示。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return errorCode.httpStatus();
    }
}
