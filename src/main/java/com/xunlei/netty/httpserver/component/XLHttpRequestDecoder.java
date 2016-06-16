package com.xunlei.netty.httpserver.component;

import java.util.Arrays;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMessageDecoder;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.netty.httpserver.util.Statistics;
import com.xunlei.util.Log;

/**
 * <pre>
 * 1.增加流量统计
 * 2.增加开始解码时间
 * </pre>
 * 
 * @author ZengDong
 * @since 2010-3-25 下午02:30:03
 */
public class XLHttpRequestDecoder extends HttpMessageDecoder {

    private static final String[] httpMethods = {
        "GET",
        "POST",
        "HEAD",
        "PUT",
        "TRACE",
        "CONNECT",
        "OPTIONS",
        "DELETE"
    };
    private static final int tryLen = 8;

    public static void main(String[] args) {
        ChannelBuffer cb = ChannelBuffers.dynamicBuffer();
        cb.writeBytes("123456GET / HTTP/1.1".getBytes());
        skipNonHttpMethodCharacters(cb);
    }

    /**
     * 尝试在解析httpRequest第一行时,把多余的字符串去除
     */
    public static void skipNonHttpMethodCharacters(ChannelBuffer buffer) {
        int idx = buffer.readerIndex();
        while (true) {
            byte[] methodTry = new byte[tryLen];
            buffer.readBytes(methodTry);
            String methodTryStr = new String(methodTry).toUpperCase();

            for (String str : httpMethods) {
                if (Character.isWhitespace((char) methodTry[str.length()]) && methodTryStr.startsWith(str)) {
                    int len = buffer.readerIndex() - tryLen - idx;
                    if (len > 0) {
                        byte[] bytes = new byte[len];
                        buffer.readerIndex(idx);

                        buffer.readBytes(bytes);
                        String skipString = new String(bytes);
                        log.warn("{}", skipString);
                    } else {
                        buffer.readerIndex(buffer.readerIndex() - tryLen);
                    }
                    return;
                }
            }
            buffer.readerIndex(buffer.readerIndex() + 1 - tryLen);
        }
    }

    private Statistics statistics;
    private static final Logger log = Log.getLogger();

    public XLHttpRequestDecoder(Statistics statistics, HttpServerConfig config) {
        super(4096, 65536, 65536);
        this.statistics = statistics;
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

    @Override
    protected HttpMessage createMessage(String[] initialLine) throws Exception {
        HttpMethod httpMethod = HttpMethod.valueOf(initialLine[0]);
        try {
            return new XLHttpRequest(HttpVersion.valueOf(initialLine[2]), httpMethod, initialLine[1]);
        } catch (Exception e) {// httpClient经常发送的http请求不严格,造成解析失败,这时这里记录下日志,并放宽限制,尽量解析成功
            String fix = initialLine[1] + " " + initialLine[2];// 这里尝试找回uri
            int result = 0;
            for (result = fix.length(); result > 0; --result) {
                if (Character.isWhitespace(fix.charAt(result - 1))) {
                    break;
                }
            }
            String version = fix.substring(result);
            for (; result > 0; --result) {
                if (!Character.isWhitespace(fix.charAt(result - 1))) {
                    break;
                }
            }
            String uri = fix.substring(0, result);
            // uri = uri.replaceAll("\t", "%09").replaceAll("\n", "%0D").replaceAll("\r", "%0A").replaceAll(" ", "+");
            log.error("parse httpRequest initialLine fail!\n\tori:{}\n\t      fix:{}\n\t      uri:{}\n\t  version:{}\n\t{}", new Object[] {
                Arrays.toString(initialLine),
                fix,
                uri,
                version,
                e.getMessage()
            });
            return new XLHttpRequest(HttpVersion.valueOf(version), httpMethod, uri);// TODO:这里有没有更好的解决办法
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
        // 一个请求会 调用多次,因此
        // [71, 69, 84, 32, 47,
        // Object attach = ctx.getAttachment();
        // if (ctx.getAttachment() != null) {
        // if (attach instanceof XLHttpRequestDecoder) {
        // ((XLHttpRequestDecoder) attach).i += 1;
        // }
        // } else {
        // ctx.setAttachment(this);
        // }
        // System.out.println(ctx);

        // 发现正式机一般是先拿1024个字节,内部应该是使用AdaptiveReceiveBufferSizePredictor来决定的
        statistics.messageReceiving(e);
        super.messageReceived(ctx, e);
    }
    // 2011-12-17 以下注释掉的方法，找开后，可以清楚地看到 发包回包整体调用顺序
    // @Override
    // protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, State state) throws Exception {
    // log.info("{}\t{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), channel, buffer, state });
    // return super.decode(ctx, channel, buffer, state);
    // }
    //
    // @Override
    // protected Object decodeLast(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, State state) throws Exception {
    // log.info("{}\t{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), channel, buffer, state });
    // return super.decodeLast(ctx, channel, buffer, state);
    // }
    //
    // @Override
    // public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelDisconnected(ctx, e);
    // }
    //
    // @Override
    // public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelClosed(ctx, e);
    // }
    //
    // @Override
    // public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.exceptionCaught(ctx, e);
    // }
    //
    // @Override
    // public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.handleUpstream(ctx, e);
    // }
    //
    // @Override
    // public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelOpen(ctx, e);
    // }
    //
    // @Override
    // public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelBound(ctx, e);
    // }
    //
    // @Override
    // public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelConnected(ctx, e);
    // }
    //
    // @Override
    // public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelInterestChanged(ctx, e);
    // }
    //
    // @Override
    // public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.channelUnbound(ctx, e);
    // }
    //
    // @Override
    // public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.writeComplete(ctx, e);
    // }
    //
    // @Override
    // public void childChannelOpen(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.childChannelOpen(ctx, e);
    // }
    //
    // @Override
    // public void childChannelClosed(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
    // log.info("{}\t{}\t{}", new Object[] { Integer.toHexString(ctx.hashCode()), e, e.getChannel() });
    // super.childChannelClosed(ctx, e);
    // }
    // 2011-12-17 以上注释掉的方法，找开后，可以清楚地看到 发包回包整体调用顺序

    // protected Object decode(final ChannelHandlerContext ctx, final Channel channel, final ChannelBuffer buffer, final State state) throws Exception {
    // // 一个httpRequest会被decode多次,因此不能在此进行超时处理
    // // ReplayingDecoder.messageReceived中会调用callDecode方法
    //
    // // 这里应该是一个请求一个obj
    // log.debug("DECODING...thread:{},channel：{},state:{}", new Object[] { Thread.currentThread().getName(), channel, state });
    // // long before = System.currentTimeMillis();
    // // try {
    // Object o = super.decode(ctx, channel, buffer, state);
    // return o;
    // // } finally {
    // // long span = System.currentTimeMillis() - before;
    // // if (span > 50) {
    // // log.error("############ decode Using " + span + "MS for channel:" + channel);
    // // }
    // // }
    // }

}
