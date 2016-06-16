package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.async.pool.SequenceChannelPool;
import com.xunlei.netty.httpserver.exception.ConnectToServerFailedError;

public class AsyncProxyHandlerBySequence extends AsyncProxyHandler<Long> {

    protected SequenceChannelPool channelPool;

    public AsyncProxyHandlerBySequence(ClientBootstrap backstageClientBootstrap, String name) {
        super(backstageClientBootstrap, name);
        this.channelPool = new SequenceChannelPool(this);
    }

    public AsyncProxyHandlerBySequence(ClientBootstrap backstageClientBootstrap, String name, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap, SequenceChannelPool channelPool) {
        super(backstageClientBootstrap, name, addressStatMap);
        this.channelPool = channelPool;
    }

    // @Override
    // public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    // // 这里捕获 channel连接及编码解码异常，这些关键步骤出问题时，为了保险起见，直接关闭此channel
    // NetUtil.exceptionCaught(ctx, e);
    // // 因为这里面找不到 seq，所以也关不了
    // }

    @Override
    public Channel getChannel(SocketAddress backstageHostAddress) {
        return channelPool.getChannel(backstageHostAddress);
    }

    @Override
    public AsyncCallbackAttach pollAsyncCallbackAttach(ChannelHandlerContext ctx, MessageEvent e) {
        SequenceMessage msg = (SequenceMessage) e.getMessage();
        long seq = msg.getSequence();
        return coreMap.remove(seq);
    }

    @Override
    public StringBuilder printStatInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(channelPool);
        sb.append("\n----------------------------------------------------\n");
        sb.append(printStatInfo(coreMap));
        return sb;
    }

    @Override
    public void submit(SocketAddress backstageHostAddress, SequenceMessage msg, AsyncCallbackAttach ca) {
        if (msg == null) {
            return;
        }
        try {
            FaultProcessorUtil.getFaultProcessor().recordReq(msg);
            Channel c = getChannel(backstageHostAddress);

            messageSendStat(backstageHostAddress);
            ca.messageSendPrepare(backstageHostAddress, msg);

            coreMap.put(msg.getSequence(), ca);
            // ca.getAttach().registerCloseable(c);// 保存这个channel放到closeable中，供超时管理器进行关闭
            // 2011-12-11 因为这里是完全双工地复用连接，所以不用在超时的时候关闭
            c.write(msg);
        } catch (ConnectToServerFailedError ex) {
            FaultProcessorUtil.dealWithFault(msg, ca, ex); // 如果处理失败或是没有处理规则，就把异常直接抛出
        }
    }

    // @Override
    // public String toString() {
    // return "AsyncProxyHandlerBySequence [channelPool=" + channelPool + ", channelPoolSize=" + channelPoolSize + ", counter=" + counter + "]";
    // }
}
