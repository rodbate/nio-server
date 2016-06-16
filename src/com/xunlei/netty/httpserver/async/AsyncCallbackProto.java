package com.xunlei.netty.httpserver.async;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler.AsyncCallbackAttach;
import com.xunlei.netty.httpserver.component.XLContextAttachment;

public abstract class AsyncCallbackProto extends AsyncCallbackAttach implements AsyncCallback {

    public static abstract class AsyncCallbackProtoAgg extends AsyncCallbackProto {

        public AsyncCallbackProtoAgg(XLContextAttachment attach) {
            super(attach);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e, XLContextAttachment attach) throws Exception {
            if (attach.asyncMessageEventReceived(e)) {
                // 所有消息都聚合完成，则可以进行下一步操作
                allMessageReceived(attach);
            }
        }

        public abstract void allMessageReceived(XLContextAttachment attach) throws Exception;
    }

    public AsyncCallbackProto(XLContextAttachment attach) {
        super(attach);
    }

    @Override
    public AsyncCallback getCallback() {
        return this;
    }
}
