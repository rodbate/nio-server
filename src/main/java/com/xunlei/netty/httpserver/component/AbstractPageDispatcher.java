package com.xunlei.netty.httpserver.component;

import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * <pre>
 * 用于计算当前url分配到哪个ICommand来处理
 * 
 * SimpleChannelUpstreamHandler中一个简单的流程：
 * 
 * channelOpen
 *  -> channelBound
 *      -> channelConnected
 *      
 *          -> messageReceived
 *              ->业务处理1
 *          -> writeComplete
 *          
 *          -> messageReceived
 *              ->业务处理2
 *          -> writeComplete
 *          
 *      -> channelDisconnected
 *  -> channelUnbound
 * channelClosed
 * 
 * 暂时还未知的状态是：
 * childChannelOpen
 * childChannelClosed
 * 
 * channelInterestChanged ：  Invoked when a Channel's interestOps was changed.
 * OP_NONE, OP_READ, OP_WRITE, or OP_READ_WRITE
 * 
 * 一个SimpleChannelUpstreamHandler在 一个 "连接"中对应一个唯一的 ChannelHandlerContext,
 * 也就是重建连接其channel变化,ctx也会变更
 * 
 * 而ChannelPipeline中的另一个 channelHandler对应的则是另一组 ctx
 * 因此 对于一个请求过来,encoder,decoder对应的ctx是不同的
 */
public abstract class AbstractPageDispatcher extends SimpleChannelUpstreamHandler {

    public abstract void init();
}
