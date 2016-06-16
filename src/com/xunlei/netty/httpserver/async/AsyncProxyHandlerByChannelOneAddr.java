package com.xunlei.netty.httpserver.async;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;

public class AsyncProxyHandlerByChannelOneAddr extends AsyncProxyHandlerByChannel {

    protected SocketAddress backstageHostAddress;

    public AsyncProxyHandlerByChannelOneAddr(ClientBootstrap backstageClientBootstrap, String name, String backstageHost, int backstagePort) {
        super(backstageClientBootstrap, name);
        this.backstageHostAddress = new InetSocketAddress(backstageHost, backstagePort);
    }

    public AsyncProxyHandlerByChannelOneAddr(ClientBootstrap backstageClientBootstrap, String name, String backstageHost, int backstagePort, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap) {
        super(backstageClientBootstrap, name, addressStatMap);
        this.backstageHostAddress = new InetSocketAddress(backstageHost, backstagePort);
    }

    protected Channel getChannel() {
        return newChannel();
    }

    protected Channel newChannel() {
        return newChannel(backstageHostAddress);
    }
}
