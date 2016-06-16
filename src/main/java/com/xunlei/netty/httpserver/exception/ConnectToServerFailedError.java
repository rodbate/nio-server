package com.xunlei.netty.httpserver.exception;

/**
 * 在业务代码中连接其他服务失败
 * 
 * @since 2012-11-12
 * @author hujiachao
 */
public class ConnectToServerFailedError extends RuntimeException {

    private static final long serialVersionUID = -4640520879235354384L;
    private String msg;

    public ConnectToServerFailedError(String msg) {
        this.msg = msg;
    }

    @Override
    public String getMessage() {
        return msg;
    }

    @Override
    public String toString() {
        return String.format("ConnectToServerFailedError [msg=%s]", msg);
    }
}
