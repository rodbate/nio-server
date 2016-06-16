package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import com.xunlei.netty.httpserver.exception.ConnectToServerFailedError;

public class AsyncProxyHandlerByChannel extends AsyncProxyHandler<Channel> {

    /**
     * <pre>
     * 用于匹配异步MessageResived时，能够找回此连接Channel对应需要响应的 attach,req,resp等信息。
     * 
     * [现不用queue]因为messageResived是ClientBootstrap中的不定的n个client worker线程在执行，因此内部使用queue来解决异步问题
     */
    public AsyncProxyHandlerByChannel(ClientBootstrap backstageClientBootstrap, String name, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap) {
        super(backstageClientBootstrap, name, addressStatMap);
    }

    public AsyncProxyHandlerByChannel(ClientBootstrap backstageClientBootstrap, String name) {
        super(backstageClientBootstrap, name);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        super.exceptionCaught(ctx, e);

        Channel c = ctx.getChannel();
        AsyncCallbackAttach ca = coreMap.get(c);
        if (ca != null) {
            // 如果连接远程server根本连接不上时，channel_attach_map时面根本一个queue都没有，这种情况要排除
            // 说明是在业务线程中发生异常，要响应给此attach（如ProxyClient 编码出错时,也就是 channel.write(msg)抛错，就会跑到此流程）
            exceptionCaught(ctx, e.getCause(), ca);
        }
    }

    @Override
    public Channel getChannel(SocketAddress backstageHostAddress) {
        return newChannel(backstageHostAddress);
    }

    /**
     * <pre>
     * 接收到后台服务器消息后，业务处理完成的后缀操作
     * 如果是 短连接，则是关闭连接
     * 如果是 使用连接池做的长连接，则是回收连接
     */
    @Override
    protected void messageReceivedFinally(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.getChannel().close();
    }

    @Override
    public AsyncCallbackAttach pollAsyncCallbackAttach(ChannelHandlerContext ctx, MessageEvent e) {
        return coreMap.remove(ctx.getChannel());
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

            coreMap.put(c, ca);
            ca.getAttach().registerCloseable(c);// 保存这个channel放到closeable中，供超时管理器进行关闭
            c.write(msg);
        } catch (ConnectToServerFailedError ex) {
            FaultProcessorUtil.dealWithFault(msg, ca, ex); // 如果处理失败或是没有处理规则，就把异常直接抛出
        }
    }

    // @Override
    // public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {// 说明开始往channel里发送请求
    // XLContextAttachment attach = getAttach(ctx, e);
    // if (attach != null) {// 这里把注册里面的信息，便于 超时时，关闭连接
    // attach.registerCloseable(Thread.currentThread());// 当前线程当成辅助线程放到closeable上
    // }
    // super.writeRequested(ctx, e);
    // }

    // public XLContextAttachment getAttach(ChannelHandlerContext ctx, MessageEvent e) {
    // Channel c = ctx.getChannel();
    // Queue<XLContextAttachment> q = channelAttachMap.get(c);
    // // 按正常流程 queue及attach肯定不能是空
    // if (q == null) {// 说明没有 registerChannelAndAttach,也就是 业务调用者没有 正确调用 getChannel(),如在外部直接 新建了一个Channel,也就是此Channel这里管理不了
    // log.error("cannot find channnel:{} mapped ATTACH QUEUE,reason:[didn't getChannel() properly],message:{}", c, e.getMessage());
    // c.close();
    // return null;
    // }
    // XLContextAttachment attach = q.peek();
    // if (attach == null) {// 说明获得此Channnel后，调用了两次channel.write(req),造成后台服务器返回了多个message
    // log.error("cannot find channnel:{} mapped ATTACH,reason:[getChannel() then send more request,i.e channel.write(req) twice],message:{}", c, e.getMessage());
    // c.close();
    // }
    // return attach;
    // }
}
