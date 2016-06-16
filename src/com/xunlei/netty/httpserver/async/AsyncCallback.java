package com.xunlei.netty.httpserver.async;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.component.XLContextAttachment;

/**
 * 在异步获得proxy请求的数据后的回调处理类
 * 
 * @author ZengDong
 * @since 2011-10-7 下午10:01:48
 */
public interface AsyncCallback {

    public static abstract class AsyncCallbackAgg implements AsyncCallback {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e, XLContextAttachment attach) throws Exception {
            if (attach.asyncMessageEventReceived(e)) {
                // 所有消息都聚合完成，则可以进行下一步操作
                allMessageReceived(attach);
            }
        }

        public abstract void allMessageReceived(XLContextAttachment attach) throws Exception;
    }

    public abstract void messageReceived(ChannelHandlerContext ctx, MessageEvent e, XLContextAttachment attach) throws Exception;

    /**
     * <pre>
     * 在找到了channel对应的attach情况下，进行事后处理
     * 
     * 
     * 注意
     * 这里的ChannelEvent有可能是ExceptionEvent（应该是ProxyClient编码出错） 或者 MessageEvent（应该是业务出错）
     * 
     * 直接改成传throwable
     */
    public abstract void exceptionCaught(ChannelHandlerContext ctx, Throwable e, XLContextAttachment attach) throws Exception;

}
