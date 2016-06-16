package com.xunlei.netty.httpserver.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * 用于中断业务处理流程，代表处理已经结束了，不需要继续执行剩余代码，直接返回给用户即可（此前的代码请保证已经写response了）
 * 
 * @since 2011-11-30
 * @author hujiachao
 */
public class ProcessFinishedError extends AbstractHttpServerError {

    private static final long serialVersionUID = 2083669464202955119L;
    public final static ProcessFinishedError INSTANCE = new ProcessFinishedError();

    private ProcessFinishedError() {
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.OK;
    }
}
