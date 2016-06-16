package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author ZengDong
 * @since 2010-11-3 下午02:20:33
 */
public class IpAuthError extends AbstractHttpServerError {

    private static final long serialVersionUID = 1L;
    public final static IpAuthError INSTANCE = new IpAuthError();

    private IpAuthError() {
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }
}
