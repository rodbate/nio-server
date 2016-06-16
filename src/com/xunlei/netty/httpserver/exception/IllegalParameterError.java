package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import com.xunlei.netty.httpserver.component.XLHttpRequest;

/**
 * @author ZengDong
 * @since 2010-5-31 下午09:54:38
 */
public class IllegalParameterError extends AbstractHttpServerError {

    private static final long serialVersionUID = 1L;
    private static final String name = IllegalParameterError.class.getSimpleName() + ":";
    @SuppressWarnings("unused")
    private XLHttpRequest request;
    private String message;

    public IllegalParameterError(String parameter, XLHttpRequest request, String type) {
        this.request = request;
        this.message = name + "NEED " + type + ":" + parameter;
    }

    public IllegalParameterError(String parameter, XLHttpRequest request, String type, String extendMessage) {
        this.request = request;
        this.message = name + type + ":'" + parameter + "'" + extendMessage;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.OK;// 此错误要返回给用户,因此是200
    }
}
