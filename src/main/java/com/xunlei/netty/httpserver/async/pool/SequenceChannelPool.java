package com.xunlei.netty.httpserver.async.pool;

import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.netty.channel.Channel;
import com.xunlei.netty.httpserver.async.XLClientHandler;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * @author ZengDong
 * @since 2012-3-23 上午10:21:49
 */
public class SequenceChannelPool {

    protected final Map<SocketAddress, Channel[]> channelPool = new ConcurrentHashMap<SocketAddress, Channel[]>();
    protected int channelPoolSize;
    protected final AtomicInteger counter;
    protected final XLClientHandler xlClientHandler;

    public SequenceChannelPool(XLClientHandler xlClientHandler) {
        this(HttpServerConfig.getAsyncPoolSize(), xlClientHandler);
    }

    public SequenceChannelPool(int channelPoolSize, XLClientHandler xlClientHandler) {
        this.channelPoolSize = channelPoolSize;
        this.xlClientHandler = xlClientHandler;
        this.counter = ConcurrentUtil.newAtomicInteger();
    }

    public Channel getChannel(SocketAddress backstageHostAddress) {
        Channel[] array = channelPool.get(backstageHostAddress);
        if (array == null) {
            synchronized (backstageHostAddress.toString().intern()) {
                array = channelPool.get(backstageHostAddress);
                if (array == null) {
                    array = new Channel[channelPoolSize];
                    channelPool.put(backstageHostAddress, array);
                }
            }
        }
        int index = counter.getAndIncrement() % array.length;
        Channel tmp = array[index];
        if (tmp == null || !tmp.isConnected()) {
            synchronized ((backstageHostAddress.toString() + index).intern()) {
                tmp = array[index];
                if (tmp == null) {
                    tmp = array[index] = xlClientHandler.newChannel(backstageHostAddress);
                } else if (!tmp.isConnected()) {
                    tmp.close(); // 理论上说这是多余的，但为了检验是不是会导致连接过多的问题，这里先试一下
                    tmp = array[index] = xlClientHandler.newChannel(backstageHostAddress);
                }
            }
        }
        return tmp;
        // Channel c = channelPool.get(backstageHostAddress);
        // if (c == null || !c.isConnected()) {
        // synchronized (backstageHostAddress.toString().intern()) {
        // c = channelPool.get(backstageHostAddress);
        // if (c == null || !c.isConnected()) {
        // c = newChannel(backstageHostAddress);
        // channelPool.put(backstageHostAddress, c);
        // }
        // }
        // }
        // return c;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SequenceChannelPool:\n");
        for (Entry<SocketAddress, Channel[]> e : channelPool.entrySet()) {
            sb.append(e.getKey()).append("\n");
            for (Channel c : e.getValue()) {
                if (c == null) {
                    sb.append("\t[EMPTY]\n");
                } else {
                    sb.append("\t").append(c);
                    if (!c.isConnected()) {
                        sb.append(" [CLOSED]");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

}
