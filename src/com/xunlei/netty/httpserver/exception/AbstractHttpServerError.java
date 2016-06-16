package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author ZengDong
 * @since 2010-11-3 下午02:21:18
 */
public abstract class AbstractHttpServerError extends Error {

    private static final long serialVersionUID = 1L;
    protected HttpResponseStatus status;

    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return this.getMessage();
    }
}
