package com.xunlei.netty.httpserver.component;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.springframework.stereotype.Service;

/**
 * 在httpserver关闭时,不处理任务请求
 */
@Service
public class NOPDispatcher extends SimpleChannelUpstreamHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.getChannel().close();// 直接关闭连接
    }
}
