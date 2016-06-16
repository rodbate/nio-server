package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler.AsyncCallbackAttach;
import com.xunlei.netty.httpserver.exception.ConnectToServerFailedError;
import com.xunlei.netty.httpserver.util.NetUtil;
import com.xunlei.util.Log;

public abstract class XLClientHandler extends SimpleChannelHandler {

    protected final Logger log = Log.getLogger(this);
    protected String name;
    /** 不同addr统计信息 */
    protected Map<SocketAddress, AsyncStat> addressStatMap;
    /** 本机代理Client */
    protected ClientBootstrap backstageClientBootstrap;

    public XLClientHandler(ClientBootstrap backstageClientBootstrap, String name) {
        this.name = name;
        this.backstageClientBootstrap = backstageClientBootstrap;
        this.addressStatMap = new ConcurrentHashMap<SocketAddress, AsyncStat>();
    }

    public XLClientHandler(ClientBootstrap backstageClientBootstrap, String name, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap) {
        this.name = name;
        this.addressStatMap = addressStatMap;
        this.backstageClientBootstrap = backstageClientBootstrap;
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.info("[{}]proxy channel is closed:{}", name, ctx.getChannel());
        SocketAddress sa = ctx.getChannel().getRemoteAddress();
        if (null != sa) {// 如果一开始就连接不上,这里获得不了addr
            getAsyncStat(sa).channelCloseCounter.incrementAndGet();
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // 这里新建连接时直接使用，而不是放到连接池上 // channelPool.offer(ctx.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        // 这里捕获 channel连接及编码解码异常，这些关键步骤出问题时，为了保险起见，直接关闭此channel
        // NetUtil.exceptionCaught(ctx, e, "asyncProxyHandler"); // 2011-12-16 不能用这个，因为内部会直接关闭channel

        // 2011-12-20 可以用这个了，因为里面没有关闭channel,但是现在的逻辑基本不应该跑到此处
        NetUtil.exceptionCaught(ctx, e, name);

        // Channel channel = e.getChannel();
        // Object attach = ctx.getAttachment();
        // Object v = attach == null ? channel : attach;// 如果有attach,就打印attach
        // log.error("exceptionCaught in async proxy:{}", new Object[] { v, e.getCause() });
    }

    public AsyncStat getAsyncStat(SocketAddress addr) {
        AsyncStat as = addressStatMap.get(addr);
        if (as == null) {
            synchronized (addressStatMap) {
                as = addressStatMap.get(addr);
                if (as == null) {
                    as = new AsyncStat(addr);
                    addressStatMap.put(addr, as);
                }
            }
        }
        return as;
    }

    public void messageSendStat(SocketAddress addr) {// 统计发包
        getAsyncStat(addr).requestCounter.incrementAndGet();
    }

    public void messageRecvStat(SocketAddress addr, AsyncCallbackAttach attach) { // 统计收包
        AsyncStat stat = getAsyncStat(addr);
        stat.responseCounter.incrementAndGet();
        stat.asyncClientStat.record(System.currentTimeMillis(), attach.getMessageSendTime(), attach);
    }

    public void messageRecvStat(SocketAddress addr, long messageSendTime, Object recordInfo) { // 统计收包,用于同步方法
        AsyncStat stat = getAsyncStat(addr);
        stat.responseCounter.incrementAndGet();
        stat.asyncClientStat.record(System.currentTimeMillis(), messageSendTime, recordInfo);
    }

    public abstract Channel getChannel(SocketAddress backstageHostAddress);

    static { // 2012-6-14 netty创建channel的过程是异步的，官方建议await*方法应该在另外的线程里面调用，但这里建立的是长连接，只是启动时建立连接的，没有必要非异步不可，故此处把检查关闭
        DefaultChannelFuture.setUseDeadLockChecker(false);
    }

    public Channel newChannel(SocketAddress backstageHostAddress) {
        ChannelFuture cf = backstageClientBootstrap.connect(backstageHostAddress);
        Channel c = cf.awaitUninterruptibly().getChannel();// 这里同步等待
        AsyncStat as = getAsyncStat(backstageHostAddress);
        if (!c.isConnected()) { // 检查当前channel的有效性
            int count = as.newChannelFailCounter.incrementAndGet();
            log.error("[{}]cannot creat a channel:{},historyCount:{}", new Object[] {
                name,
                backstageHostAddress,
                count
            });
            throw new ConnectToServerFailedError("[" + name + "]cannt creat a channel:" + backstageHostAddress + ",historyCount:" + count);
        }
        int count = as.newChannelOkCounter.incrementAndGet();
        log.debug("[{}]create a channel:{},historyCount:{}", new Object[] {
            name,
            c,
            count
        });
        return c;
    }

    // public Channel newChannel(final SocketAddress backstageHostAddress) {
    // ChannelFuture cf = backstageClientBootstrap.connect(backstageHostAddress);
    // final Channel[] c = new Channel[1];
    // cf.addListener(new ChannelFutureListener() {
    //
    // @Override
    // public void operationComplete(ChannelFuture cf1) throws Exception {
    // Channel ch = cf1.awaitUninterruptibly().getChannel();// 这里同步等待
    // AsyncStat as = getAsyncStat(backstageHostAddress);
    // if (!ch.isConnected()) { // 检查当前channel的有效性
    // int count = as.newChannelFailCounter.incrementAndGet();
    // log.error("[{}]cannot creat a channel:{},historyCount:{}", new Object[] { name, backstageHostAddress, count });
    // throw new RuntimeException("[" + name + "]cannt creat a channel:" + backstageHostAddress + ",historyCount:" + count);
    // }
    // int count = as.newChannelOkCounter.incrementAndGet();
    // log.debug("[{}]create a channel:{},historyCount:{}", new Object[] { name, c, count });
    // c[0] = ch;
    // }
    // });
    // while (null == c[0]) {
    // }
    // return c[0];
    // }

    // @Override
    // public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
    // // 这里编码完成后，还要等待realServer响应- MessageResived，暂不放回到连接池上// channelPool.offer(ctx.getChannel());
    // // 以下代码用于统计用
    // getClientAttach(ctx).writeComplete(); // 2012-04-18 这个是一开始最原始想法，其实是错的，writeComplete不能认为就是 发一个整体请求包完毕
    // }

    // @Override
    // public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {// 2012-04-18 这个是一开始最原始想法，其实是错的，现在已经不需要XLClientContextAttachment了
    // // 统计接收到了远程的响应
    // Channel c = ctx.getChannel();
    // XLClientContextAttachment clientAttach = (XLClientContextAttachment) ctx.getAttachment();
    // if (clientAttach == null) {
    // log.error("cannot find clientAttach when messageReceived,channel:{},message:{}", c, e.getMessage());
    // } else {
    // clientAttach.messageReceived(e);
    // }
    // }

    // /**
    // * 从ctx中获得clientAttach，没有则新建一个
    // */
    // public XLClientContextAttachment getClientAttach(ChannelHandlerContext ctx) {
    // XLClientContextAttachment clientAttach = (XLClientContextAttachment) ctx.getAttachment();
    // if (clientAttach == null) {
    // SocketAddress addr = ctx.getChannel().getRemoteAddress();
    // XLClientContextAttachment newAttach = new XLClientContextAttachment(ctx, getAsyncStat(addr));
    // ctx.setAttachment(newAttach);
    // return newAttach;
    // }
    // return clientAttach;
    // }

    /**
     * 获得统计信息
     */
    public StringBuilder printStatInfo() {
        StringBuilder sb = new StringBuilder();
        for (AsyncStat as : addressStatMap.values()) {
            sb.append(as).append("\n\n");
        }
        return sb;
    }
}
