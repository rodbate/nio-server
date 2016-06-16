package com.xunlei.netty.httpserver.exception;

/**
 * 退化处理器，处理时还是失败进报的错误
 */
public class FaultProcessorError extends RuntimeException {

    private static final long serialVersionUID = -5800915413133379393L;

    public FaultProcessorError(String message, Throwable cause) {
        super(message, cause);
    }
}
