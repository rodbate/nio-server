package com.xunlei.netty.httpserver.component;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import com.xunlei.netty.httpserver.cmd.CmdMappers;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.netty.httpserver.util.NetUtil;

/**
 * 命令分发器的一般处理
 * 
 * <pre>
 * netty中bind一个端口就有一个 boss线程一直在等待新的连接(NioServerSocketPipelineSink),当有新的任务来时(registerAcceptedChannel),会找nextWorker
 * 这个 NioWorker.register(channel)来处理这个任务,然后内部用worker线程池来进行业务处理
 * 
 * 线程名叫：New I/O server worker #1-4,其中1表示bossid,4表示workerid
 * 
 * 测试发现：如果worker里面一直处理(占用了通道时),在有新的请求时,boss根本分配不了任务
 * 
 * channelOpen -- channelBound --channelConnected
 * channelDisconnected -- channelUnbound -- channelClosed
 * disconnected 说明自己已经开始主动关闭连接了，应该是进入time_wait状态
 * channelClosed 说明才是真正的释放了连接
 * 
 * time_wait问题
 * http://wenku.baidu.com/view/72333049e45c3b3567ec8b65.html
 * 
 * net.ipv4.tcp_tw_reuse = 1
 * net.ipv4.tcp_tw_recycle = 1
 * net.ipv4.tcp_fin_timeout = 30
 * net.ipv4.tcp_max_tw_buckets = 1000
 * </pre>
 * 
 * <pre>
 * 严重注意，在pipeline前的线程池必须是 OMAT 有序的，原因是：在此类中不同的事件方法中对顺序特别敏感：
 *    如channelOpen 会新建一个Attach,messageReceived会对此Attach进行再处理，如果把线程池改成无序的话，就有很多请求报错：
 *    
 * 17:38:46|.httpserver.util.NetUtil.exceptionCaught|ERROR exceptionCaught   :[id: 0x4ccf31e5, /119.145.40.161:3157 => /61.147.81.107:80] (NetUtil.java:105) PIPELINE#2 
 * java.lang.NullPointerException: null
 *     at com.xunlei.netty.httpserver.component.BasePageDispatcher.messageReceived(BasePageDispatcher.java:107) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.execution.ChannelUpstreamEventRunnable.run(ChannelUpstreamEventRunnable.java:44) [netty-3.4.6.Final.jar:na]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:886) [na:1.6.0_32]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:908) [na:1.6.0_32]
 *     at java.lang.Thread.run(Thread.java:662) [na:1.6.0_32]
 *     
 *     
 *  另外 messageReceived也有Chunk的情况：
 *  17:38:06|.httpserver.util.NetUtil.exceptionCaught|ERROR exceptionCaught   :null-d10f642/119.145.40.161:48615/decode:993(10|993,0,0) (NetUtil.java:105) PIPELINE#66 
 * java.lang.IndexOutOfBoundsException: null
 *     at org.jboss.netty.buffer.AbstractChannelBuffer.readerIndex(AbstractChannelBuffer.java:44) ~[netty-3.4.6.Final.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.readFileUploadByteMultipart(HttpPostRequestDecoder.java:1153) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.getFileUpload(HttpPostRequestDecoder.java:993) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.decodeMultipart(HttpPostRequestDecoder.java:687) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.parseBodyMultipart(HttpPostRequestDecoder.java:589) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.parseBody(HttpPostRequestDecoder.java:419) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.offer(HttpPostRequestDecoder.java:369) ~[xl-ruiz-1.0.jar:na]
 *     at com.xunlei.netty.httpserver.component.XLHttpRequest.offerChunk(XLHttpRequest.java:533) ~[xl-ruiz-1.0.jar:na]
 *     at com.xunlei.netty.httpserver.component.BasePageDispatcher.messageReceived(BasePageDispatcher.java:123) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.execution.ChannelUpstreamEventRunnable.run(ChannelUpstreamEventRunnable.java:44) [netty-3.4.6.Final.jar:na]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:886) [na:1.6.0_32]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:908) [na:1.6.0_32]
 *     at java.lang.Thread.run(Thread.java:662) [na:1.6.0_32]
 * 17:38:06|.httpserver.util.NetUtil.exceptionCaught|ERROR exceptionCaught   :null-d10f642/119.145.40.161:48615/decode:993(10|993,0,0) (NetUtil.java:105) PIPELINE#1 
 * java.lang.NullPointerException: null
 *     at org.jboss.netty.handler.codec.http.AbstractDiskHttpData.addContent(AbstractDiskHttpData.java:159) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.MixedFileUpload.addContent(MixedFileUpload.java:70) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.readFileUploadByteMultipart(HttpPostRequestDecoder.java:1151) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.getFileUpload(HttpPostRequestDecoder.java:993) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.decodeMultipart(HttpPostRequestDecoder.java:687) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.parseBodyMultipart(HttpPostRequestDecoder.java:589) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.parseBody(HttpPostRequestDecoder.java:419) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.codec.http.HttpPostRequestDecoder.offer(HttpPostRequestDecoder.java:369) ~[xl-ruiz-1.0.jar:na]
 *     at com.xunlei.netty.httpserver.component.XLHttpRequest.offerChunk(XLHttpRequest.java:533) ~[xl-ruiz-1.0.jar:na]
 *     at com.xunlei.netty.httpserver.component.BasePageDispatcher.messageReceived(BasePageDispatcher.java:123) ~[xl-ruiz-1.0.jar:na]
 *     at org.jboss.netty.handler.execution.ChannelUpstreamEventRunnable.run(ChannelUpstreamEventRunnable.java:44) [netty-3.4.6.Final.jar:na]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:886) [na:1.6.0_32]
 *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:908) [na:1.6.0_32]
 *     at java.lang.Thread.run(Thread.java:662) [na:1.6.0_32]
 * </pre>
 * 
 * @author ZengDong
 * @since 2010-3-18 下午01:41:21
 */
public abstract class BasePageDispatcher extends AbstractPageDispatcher {

    // private static final Logger log = Log.getLogger();
    @Autowired
    protected CmdMappers cmdMappers;
    @Autowired
    protected HttpServerConfig config;

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        config.getStatistics().channelClosed(ctx, e);
        XLContextAttachment attach = (XLContextAttachment) ctx.getAttachment();
        if (null != attach) {
            timeoutInterrupter.getAttachRegister().unregisterAttach(attach);
            if (attach.interrupt(null)) {
                config.getStatistics().channelInterruptClosed(null); // 内部未使用，传null即可
            }
            XLHttpRequest request = attach.getRequest(); // 不单在 response writeComplete时需要clean，在断开连接时也需要(有些请求是没等回包就断开连接了)
            if (request != null) {
                request.clean();
            }
        }
        super.channelClosed(ctx, e);
    }

    /**
     * <pre>
     * 在没有被前面的messageReceived中的catch Throwable的throwableHandlerManager截获掉的情况下,会进放到此方法
     * 
     * 也就是这里不处理 业务异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        NetUtil.exceptionCaught(ctx, e);
        ctx.getChannel().close();
    }

    @Autowired
    private TimeoutInterrupter timeoutInterrupter;

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        config.getStatistics().channelOpen(ctx, e);
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        XLContextAttachment attach = getAttach(ctx);
        ctx.setAttachment(attach);// 设置附着对象
        timeoutInterrupter.getAttachRegister().registerAttach(attach);
        super.channelConnected(ctx, e);
    }

    protected abstract void dispatch(XLContextAttachment attach) throws Exception;

    @Override
    public void init() {
    }

    // 2012-05-31 为了使得此类可以不用 OrderMemoryAwareExecutor，而改用 MemoryAwareExecutor,不能保证 事件顺序的情况下，attach需要处理并发情况(channelOpen及messageReceivedt等事件随时可能是乱序的)
    public XLContextAttachment getAttach(ChannelHandlerContext ctx) {
        XLContextAttachment attach = (XLContextAttachment) ctx.getAttachment();
        if (attach == null) {
            synchronized (ctx) {
                attach = (XLContextAttachment) ctx.getAttachment();
                if (attach == null) {
                    attach = new XLContextAttachment(ctx);
                    ctx.setAttachment(attach);// 设置附着对象
                }
            }
        }
        return attach;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        XLContextAttachment attach = getAttach(ctx);
        Object obj = messageEvent.getMessage();
        try {
            if (obj instanceof XLHttpRequest) {
                XLHttpRequest request = (XLHttpRequest) obj;
                request.setRemoteAddress(messageEvent.getRemoteAddress());
                request.setLocalAddress(messageEvent.getChannel().getLocalAddress());
                boolean userAgentTryPipelining = attach.registerNewMessage(request);
                if (userAgentTryPipelining) {
                    // 忽略此请求
                    return;
                }

                if (request.isChunked()) {// 说明是在上传文件
                    // TODO:这里是不是要限制上传文件大小
                } else {
                    requestReceived(ctx, attach, request, messageEvent);
                }
            }

            // 2012-05-31 对HttpChunk的聚合不宜放到此类，此类是业务处理类，为了性能考虑会在线程池下游跑，并且设置成 事件顺序不敏感的（不用OrderMemoryAwareExecutor）
            // else if (obj instanceof HttpChunk) {
            //
            // XLHttpRequest request = attach.getRequest();
            // HttpChunk httpChunk = (HttpChunk) obj;
            //
            // request.offerChunk(httpChunk);
            //
            // attach.markLastReadTime();
            //
            // if (httpChunk.isLast()) {
            // requestReceived(ctx, attach, request, messageEvent);
            // }
            // }

            else {
                throw new RuntimeException("cant resolve message:" + obj);
            }
        } finally {
            ctx.sendUpstream(messageEvent);// 确保所有handler都流过
        }
    }

    private void requestReceived(ChannelHandlerContext ctx, XLContextAttachment attach, XLHttpRequest request, MessageEvent messageEvent) throws Exception {
        XLHttpResponse response = new XLHttpResponse(attach);
        attach.registerNewMessage(response);

        config.getStatistics().messageReceived(attach);
        // try {
        dispatch(attach);
        // } finally {
        // ctx.sendUpstream(messageEvent);// 确保所有handler都流过
        // }
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
        // 这里一个响应可能会分拆成多次来发送
        XLContextAttachment attach = getAttach(ctx);
        // if (attach != null) //不考虑
        config.getStatistics().writeComplete(attach, e);
        super.writeComplete(ctx, e);
    }
}
