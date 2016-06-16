package com.xunlei.netty.httpserver.async;

/**
 * 发包回包时有带 seq的消息体
 * 
 * @author ZengDong
 * @since 2011-12-1 下午3:20:39
 */
public interface SequenceMessage {

    public long getSequence();
}
