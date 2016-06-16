package com.xunlei.netty.httpserver.async.pool;

import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.async.XLClientHandler;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.util.Log;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * @author ZengDong
 * @since 2012-3-23 上午10:21:49
 */
public class FifoChannelPool {

    private static final Logger log = Log.getLogger();
    /**
     * 连接池，用于重用代理连接
     */
    private final Map<SocketAddress, ConcurrentLinkedQueue<Channel>> channelPool = new ConcurrentHashMap<SocketAddress, ConcurrentLinkedQueue<Channel>>();
    protected final XLClientHandler xlClientHandler;

    public FifoChannelPool(XLClientHandler xlClientHandler) {
        this.xlClientHandler = xlClientHandler;
        init();
    }

    private void init() {
        int delay = HttpServerConfig.getAsyncProxyPoolChannelSwepperDelaySeconds();
        ConcurrentUtil.getDaemonExecutor().scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                for (ConcurrentLinkedQueue<Channel> q : channelPool.values()) {
                    while (q.size() > HttpServerConfig.getAsyncProxyPoolChannelCoreNum()) {
                        Channel c = q.poll();
                        if (c != null) {
                            c.close();// 回收channel
                        }
                    }
                }
            }
        }, delay, delay, TimeUnit.SECONDS);
    }

    public void offerChannel(Channel c) {
        SocketAddress backstageHostAddress = c.getRemoteAddress();
        ConcurrentLinkedQueue<Channel> queue = channelPool.get(backstageHostAddress);
        if (queue != null) {
            queue.offer(c);
        }
    }

    public Channel getChannel(SocketAddress backstageHostAddress) {
        ConcurrentLinkedQueue<Channel> queue = null;
        synchronized (backstageHostAddress.toString().intern()) {
            queue = channelPool.get(backstageHostAddress);
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<Channel>();
                channelPool.put(backstageHostAddress, queue);
            }
        }

        Channel c = queue.poll();
        if (c == null) {
            log.debug("channelPoll hasn't any available channel,creat one![{}]", backstageHostAddress);
            return xlClientHandler.newChannel(backstageHostAddress);
        }
        if (!c.isConnected()) {
            log.debug("channelPoll has one unavailable channel:{},creat another!", c);
            return xlClientHandler.newChannel(backstageHostAddress);
        }
        log.debug("channelPoll has one available channel:{}", c);
        return c;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FifoChannelPool:\n");
        for (Entry<SocketAddress, ConcurrentLinkedQueue<Channel>> e : channelPool.entrySet()) {
            sb.append(e.getKey()).append("\n");
            for (Channel c : e.getValue()) {
                // if (c == null) {
                // sb.append("\t[EMPTY]\n");
                // } else {
                sb.append("\t").append(c);
                if (!c.isConnected()) {
                    sb.append(" [CLOSED]");
                }
                sb.append("\n");
                // }
            }
        }
        return sb.toString();
    }

}
