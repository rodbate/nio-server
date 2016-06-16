package com.xunlei.netty.httpserver.util;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import com.xunlei.netty.httpserver.component.XLContextAttachment;

/**
 * @author ZengDong
 * @since 2010-5-20 下午03:34:28
 */
public interface Statistics {

    /**
     * 上报打开通道
     */
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e);

    /**
     * 上报开始接收消息,统计接收数据的流量
     */
    public void messageReceiving(MessageEvent e);

    /**
     * 上报接收消息完成,统计解码用时
     */
    public void messageReceived(XLContextAttachment attach);

    /**
     * 上报开始发送消息,统计业务用时
     */
    public void writeBegin(XLContextAttachment attach);

    /**
     * 上报发送消息完成,统计编码用时
     */
    public void writeComplete(XLContextAttachment attach, WriteCompletionEvent e);

    /**
     * 上报处理中,发现通道被提前关闭
     */
    public void channelInterruptClosed(ChannelHandlerContext ctx);

    /**
     * 上报关闭通道
     */
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e);

}
