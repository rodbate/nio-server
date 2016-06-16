package com.xunlei.netty.httpserver.component;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

/**
 * @author ZengDong
 * @since 2010-5-23 上午08:55:34
 */
public class XLHttpResponseEncoder extends HttpResponseEncoder {

    // private Statistics statistics;
    // private static final Logger log = Log.getLogger();

    @Override
    protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception {
        super.encodeInitialLine(buf, message);
    }

    // protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
    // log.debug("ENCODING...thread:{},channel：{},msg:{}", new Object[] { Thread.currentThread().getName(), channel, msg });
    //
    // // long before = System.currentTimeMillis();
    // // try {
    // Object o = super.encode(ctx, channel, msg);
    // return o;
    // // } finally {
    // // long span = System.currentTimeMillis() - before;
    // // if (span > 50) {
    // // log.error("############ encode Using " + span + "MS for channel:" + channel);
    // // }
    // // }
    // }

    // @Override
    // public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
    // super.handleDownstream(ctx, evt);
    // }

}
