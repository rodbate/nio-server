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
public class NOPStatistics implements Statistics {

    public static final Statistics INSTANCE = new NOPStatistics();

    private NOPStatistics() {
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
    }

    @Override
    public void channelInterruptClosed(ChannelHandlerContext ctx) {
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
    }

    @Override
    public void messageReceived(XLContextAttachment attach) {
    }

    @Override
    public void messageReceiving(MessageEvent e) {
    }

    @Override
    public void writeBegin(XLContextAttachment attach) {
    }

    @Override
    public void writeComplete(XLContextAttachment attach, WriteCompletionEvent e) {
    }
}
