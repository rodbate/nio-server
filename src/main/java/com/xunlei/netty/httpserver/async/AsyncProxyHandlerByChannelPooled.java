package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.async.pool.FifoChannelPool;

public class AsyncProxyHandlerByChannelPooled extends AsyncProxyHandlerByChannel {

    private FifoChannelPool channelPool;

    public AsyncProxyHandlerByChannelPooled(ClientBootstrap backstageClientBootstrap, String name, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap, FifoChannelPool channelPool) {
        super(backstageClientBootstrap, name, addressStatMap);
        this.channelPool = channelPool;
    }

    public AsyncProxyHandlerByChannelPooled(ClientBootstrap backstageClientBootstrap, String name) {
        super(backstageClientBootstrap, name);
        this.channelPool = new FifoChannelPool(this);
    }

    @Override
    public Channel getChannel(SocketAddress backstageHostAddress) {
        return channelPool.getChannel(backstageHostAddress);
    }

    @Override
    protected void messageReceivedFinally(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        channelPool.offerChannel(ctx.getChannel());
    }

    @Override
    public StringBuilder printStatInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(channelPool);
        sb.append("\n----------------------------------------------------\n");
        sb.append(super.printStatInfo());
        return sb;
    }
}
