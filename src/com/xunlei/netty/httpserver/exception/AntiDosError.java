package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author ZengDong
 * @since 2010-11-3 下午02:20:33
 */
public class AntiDosError extends AbstractHttpServerError {

    private static final long serialVersionUID = 1L;
    public final static AntiDosError INSTANCE = new AntiDosError();

    private AntiDosError() {
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }
}
