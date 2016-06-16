package com.xunlei.netty.httpserver.exception;

public class TrimTimeoutError extends RuntimeException {

    private static final long serialVersionUID = -4640520879235354384L;
    public static final TrimTimeoutError INSTANCE = new TrimTimeoutError();

    public TrimTimeoutError() {
    }
}
