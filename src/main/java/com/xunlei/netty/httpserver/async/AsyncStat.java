package com.xunlei.netty.httpserver.async;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import com.xunlei.util.Log;
import com.xunlei.util.stat.TimeSpanStat;

/**
 * @author ZengDong
 * @since 2012-3-23 上午9:51:50
 */
public class AsyncStat {

    private final Logger logger = Log.getLogger();
    private SocketAddress address;
    protected TimeSpanStat asyncClientStat;

    protected AtomicInteger channelCloseCounter = new AtomicInteger();
    protected AtomicInteger newChannelFailCounter = new AtomicInteger();
    protected AtomicInteger newChannelOkCounter = new AtomicInteger();

    protected AtomicInteger requestCounter = new AtomicInteger();
    protected AtomicInteger responseCounter = new AtomicInteger();
    protected AtomicInteger timeoutCounter = new AtomicInteger();

    public AsyncStat(SocketAddress address) {
        this.address = address;
        InetSocketAddress addr = (InetSocketAddress) address;
        this.asyncClientStat = new TimeSpanStat(addr.getHostName(), logger);
    }

    @Override
    public String toString() {
        return String.format("%s:\nnewChannelOkCounter=%s\nnewChannelFailCounter=%s\nchannelCloseCounter=%s\nrequestCounter=%s\nresponseCounter=%s\ntimeoutCounter=%s\nasyncClientStat:\n%s%s",
                address, newChannelOkCounter, newChannelFailCounter, channelCloseCounter, requestCounter, responseCounter, timeoutCounter, asyncClientStat.getTableHeader(), asyncClientStat);
    }
}
