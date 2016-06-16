package com.xunlei.netty.httpserver.exception;

/**
 * 在业务代码中异步回包超时
 */
public class FaultProcessorTimeoutError extends RuntimeException {

    private static final long serialVersionUID = -4640520879235354384L;
    public static final FaultProcessorTimeoutError INSTANCE = new FaultProcessorTimeoutError("");
    private String msg;

    public FaultProcessorTimeoutError(String msg) {
        this.msg = msg;
    }

    @Override
    public String getMessage() {
        return msg;
    }

    @Override
    public String toString() {
        return String.format("FaultProcessorTimeoutError [msg=%s]", msg);
    }
}
