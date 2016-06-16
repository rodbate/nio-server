package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author ZengDong
 * @since 2010-11-3 下午02:20:33
 */
public class ClosedChannelError extends AbstractHttpServerError {

    private static final long serialVersionUID = 1L;
    public final static ClosedChannelError INSTANCE = new ClosedChannelError();

    private ClosedChannelError() {
    }

    @Override
    public HttpResponseStatus getStatus() {
        throw this;// 直接抛出,让BasePageDispatcher在 exceptionCaught中统一打日志
    }

}
