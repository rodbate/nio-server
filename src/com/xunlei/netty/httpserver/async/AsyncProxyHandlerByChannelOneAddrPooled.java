package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.async.pool.FifoChannelPool;

/**
 * @author ZengDong
 * @since 2011-10-7 下午02:59:40
 */
public class AsyncProxyHandlerByChannelOneAddrPooled extends AsyncProxyHandlerByChannelOneAddr {

    private FifoChannelPool channelPool;

    public AsyncProxyHandlerByChannelOneAddrPooled(ClientBootstrap backstageClientBootstrap, String name, String backstageHost, int backstagePort) {
        super(backstageClientBootstrap, name, backstageHost, backstagePort);
        this.channelPool = new FifoChannelPool(this);
    }

    public AsyncProxyHandlerByChannelOneAddrPooled(ClientBootstrap backstageClientBootstrap, String name, String backstageHost, int backstagePort,
            ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap, FifoChannelPool channelPool) {
        super(backstageClientBootstrap, name, backstageHost, backstagePort, addressStatMap);
        this.channelPool = channelPool;
    }

    @Override
    protected Channel getChannel() {
        return getChannel(backstageHostAddress);
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
