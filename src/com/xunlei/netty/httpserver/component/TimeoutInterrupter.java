package com.xunlei.netty.httpserver.component;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.handler.TextResponseHandlerManager;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;
import com.xunlei.util.concurrent.ConcurrentHashSet;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * <pre>
 * 注意：
 * 1.如果一开始disable时,后期在运行时再打开时,因为原来保持的长连接没有注册到liveAttach中,这些长连接不会被TimeoutInterrupter扫描到而关闭
 * 2.keepAliveTimeout不会用于判断是否打开TimeoutInterrupter的依据
 * 
 * @author ZengDong
 * @since 2011-3-17 下午09:56:59
 */
@Service
public class TimeoutInterrupter {

    private static TimeoutInterrupter INSTANCE;

    private TimeoutInterrupter() {
        INSTANCE = this;
    }

    public static TimeoutInterrupter getInstance() {
        return INSTANCE;
    }

    @Autowired
    protected TextResponseHandlerManager handlerManager;
    @Autowired
    protected HttpServerConfig config;

    public interface AttachRegister {

        public void registerAttach(XLContextAttachment attach);

        public void unregisterAttach(XLContextAttachment attach);
    }

    private static final Logger log = Log.getLogger();
    private Runnable _defaultInterrupter = new Runnable() {

        private void close(XLContextAttachment attach, String tips) {
            Channel channel = attach.getChannelHandlerContext().getChannel();
            channel.close();
            sweepedChannelNum++;
            log.warn("close {} attach:{}", tips, attach);
        }

        /**
         * <pre>
         *       |<-----------------------------------ReaderIdleTimeout-------------------------------->|
         *                                                                      |<----------------------------------------WriterIdleTimeout---------------------------->|
         * messageReceived - processThreadBegin ------- processThreadEnd - writeComplete ---------messageReceived - processThreadBegin ------- processThreadEnd - writeComplete
         *                           |<-------cmdTimeout------>|                |<---AllIdleTimeout---->|
         *                                                                      |<---keepAliveTimeout-->|
         * 
         * 从上图可以看出,一般情况下：readerIdleTimeSeconds,writerIdleTimeSeconds > allIdleTimeSeconds > keepAliveTimeout
         */
        @Override
        public void run() {
            // log.info("start TimeoutInterrupter,liveAttachNum:{}", currentChannelsNum());
            try {
                for (Iterator<XLContextAttachment> it = liveAttach.iterator(); it.hasNext();) {
                    XLContextAttachment attach = it.next();
                    // 如果发现连接已经被断开了，就移除之
                    Channel channel = attach.getChannelHandlerContext().getChannel();
                    if (!channel.isConnected()) {
                        it.remove();
                        log.debug("removed closed channel {}", channel);
                        continue;
                    }
                    if (attach.isNotProcessing()) {// 判断其是否io空闲
                        long read = attach.getLastReadTime();
                        long write = attach.getLastWriteTime();
                        long all = Math.max(read, write);
                        int allTimeout = allIdleTimeSeconds;
                        long now = System.currentTimeMillis();
                        XLHttpResponse response = attach.getResponse();
                        if (response == null) {// 现在版本限制得很严格,这里只要发现 response非空,readerIdleTimeSeconds和writerIdleTimeSeconds都不会起作用
                            if (readerIdleTimeSeconds > 0) {
                                if (now - read > readerIdleTimeSeconds * 1000) {
                                    close(attach, "readIdle-" + readerIdleTimeSeconds);
                                    continue;
                                }
                            }
                            if (writerIdleTimeSeconds > 0) {
                                if (now - write > writerIdleTimeSeconds * 1000) {
                                    close(attach, "writeIdle-" + writerIdleTimeSeconds);
                                    continue;
                                }
                            }
                        } else {
                            int keepAliveTimeout = response.getKeepAliveTimeout();
                            allTimeout = Math.max(allIdleTimeSeconds, keepAliveTimeout); // 保险起见，取大者来清理过期的attach
                        }

                        if (allTimeout > 0) {// 如果有设置要allIdle timeout
                            if (now - all > allTimeout * 1000) {
                                close(attach, "allIdle-" + allTimeout);
                                continue;
                            }
                        }
                    } else { // 说明其在业务处理
                        CmdMeta cm = attach.getCmdMeta();
                        if (null != cm) { // 2012-11-12 如果为null，说明处于messageReceived和_dispath之间，一般不会阻塞，不处理，之前没加这个判断会导致抛出异常中断扫描线程
                            long timeout = cm.getTimeout();
                            if (!channel.isOpen()) {// 远程已经关闭了此channel,所以中断里面的线程
                                StringBuilder info = new StringBuilder();
                                attach.interrupt(info);
                                it.remove(); // 2012-12-25 以下新增从liveAttach清理掉
                                log.warn("interrupt {} [channelClosed] {}", new Object[] {
                                    attach,
                                    info
                                });// TODO:这里统计不到config上"通道被提前关闭:"
                            } else if (timeout > 0) {
                                long span = System.currentTimeMillis() - attach.getProcess();
                                if (span > timeout * 1000) {
                                    StringBuilder info = new StringBuilder();
                                    attach.interrupt(info);// 通知关闭所有closable及线程 //TODO:这里是否返回数据到客户端
                                    channel.close(); // 2012-12-25 以下新增从liveAttach清理掉
                                    it.remove();
                                    log.warn("interrupt {} [{}ms] {}", new Object[] {
                                        attach,
                                        span,
                                        info
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                log.error("", e);
            }
        }
    };
    public final AttachRegister _defaultRegister = new AttachRegister() {

        @Override
        public void registerAttach(XLContextAttachment attach) {
            liveAttach.add(attach);
        }

        @Override
        public void unregisterAttach(XLContextAttachment attach) {
            if (!liveAttach.remove(attach)) {// 没有remove成功，打出日志来检查下
                log.error("unregisterAttach fail,attach:\n{}", attach.getDetailInfo());
            }
        }
    };

    public final AttachRegister _nopRegister = new AttachRegister() {

        @Override
        public void registerAttach(XLContextAttachment attach) {
        }

        @Override
        public void unregisterAttach(XLContextAttachment attach) {
        }
    };
    @Config(resetable = true)
    public volatile int allIdleTimeSeconds = 0;
    private AttachRegister attachRegister = _nopRegister;
    /**
     * 所有还在生命周期内的attach
     */
    private final Set<XLContextAttachment> liveAttach = new ConcurrentHashSet<XLContextAttachment>();
    @Config(resetable = true)
    public volatile int readerIdleTimeSeconds = 0;
    private ScheduledFuture<?> scheduledFuture;
    private volatile long sweepedChannelNum;
    /** 超时扫描时间间隔，单位秒 */
    @Config(resetable = true)
    private int sweepFrequencySeconds = 1;
    private Boolean threadInterrupterEnable;
    @Config(resetable = true)
    public volatile int writerIdleTimeSeconds = 0;

    public int currentChannelsNum() {
        return liveAttach.size();
    }

    public AttachRegister getAttachRegister() {
        return attachRegister;
    }

    public boolean isEnable() {
        return threadInterrupterEnable || readerIdleTimeSeconds > 0 || allIdleTimeSeconds > 0 || writerIdleTimeSeconds > 0;
    }

    private synchronized boolean reset() {
        // 在nettyHttpServer启动时，会在 spring的配置期间调用一次，又在
        // setThreadInterrupterEnable<-CmdMapper.resetCmdConfig<-CmdMapperDispatcher.init() 调用一次
        // 为了让日志只打印一次，在threadInterrupterEnable
        // 还没有初始化的情况下，不动
        if (threadInterrupterEnable == null) {
            return false;
        }
        boolean result = isEnable();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }

        if (result) {
            attachRegister = _defaultRegister;
            log.warn("TimeoutInterrupter      ON,ioIdle:{},{},{},cmdTimeout:{}", new Object[] {
                readerIdleTimeSeconds,
                writerIdleTimeSeconds,
                allIdleTimeSeconds,
                threadInterrupterEnable
            });
            scheduledFuture = ConcurrentUtil.getDaemonExecutor().scheduleWithFixedDelay(_defaultInterrupter, sweepFrequencySeconds, sweepFrequencySeconds, TimeUnit.SECONDS);
        } else {
            attachRegister = _nopRegister;
            liveAttach.clear();
            // sweepedChannelNum 不变
            log.warn("TimeoutInterrupter      OFF");
        }
        return result;
    }

    public void setThreadInterrupterEnable(boolean threadInterrupterEnable) {
        int ori = hashCode();
        this.threadInterrupterEnable = threadInterrupterEnable;
        int now = hashCode();
        if (ori != now) {
            reset();
        }
    }

    public long sweepedChannelNum() {
        return sweepedChannelNum;
    }

    public synchronized void sweepedChannelNumIncr(int count) {
        sweepedChannelNum += count;
    }

    public int getAllIdleTimeSeconds() {
        return allIdleTimeSeconds;
    }

    public int getReaderIdleTimeSeconds() {
        return readerIdleTimeSeconds;
    }

    public long getSweepedChannelNum() {
        return sweepedChannelNum;
    }

    public int getSweepFrequencySeconds() {
        return sweepFrequencySeconds;
    }

    public int getWriterIdleTimeSeconds() {
        return writerIdleTimeSeconds;
    }

    public void setAllIdleTimeSeconds(int allIdleTimeSeconds) {// 实现可实时配置,这里发现 其值有变动
        int ori = hashCode();
        this.allIdleTimeSeconds = allIdleTimeSeconds;
        int now = hashCode();
        if (ori != now) {
            reset();
        }
    }

    public void setReaderIdleTimeSeconds(int readerIdleTimeSeconds) { // 实现可实时配置,这里发现 其值有变动
        int ori = hashCode();
        this.readerIdleTimeSeconds = readerIdleTimeSeconds;
        int now = hashCode();
        if (ori != now) {
            reset();
        }
    }

    public void setWriterIdleTimeSeconds(int writerIdleTimeSeconds) { // 实现可实时配置,这里发现 其值有变动
        int ori = hashCode();
        this.writerIdleTimeSeconds = writerIdleTimeSeconds;
        int now = hashCode();
        if (ori != now) {
            reset();
        }
    }

    public void setSweepFrequencySeconds(int sweepFrequency) {
        int ori = hashCode();
        this.sweepFrequencySeconds = sweepFrequency;
        int now = hashCode();
        if (ori != now) {
            reset();
        }
    }

    public Set<XLContextAttachment> getLiveAttach() {
        return liveAttach;
    }

    public void removeAttach(XLContextAttachment attach) {
        liveAttach.remove(attach);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + allIdleTimeSeconds;
        result = prime * result + readerIdleTimeSeconds;
        result = prime * result + sweepFrequencySeconds;
        result = prime * result + writerIdleTimeSeconds;
        result = prime * result + ((threadInterrupterEnable == null) ? 0 : threadInterrupterEnable.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TimeoutInterrupter other = (TimeoutInterrupter) obj;
        if (allIdleTimeSeconds != other.allIdleTimeSeconds) {
            return false;
        }
        if (readerIdleTimeSeconds != other.readerIdleTimeSeconds) {
            return false;
        }
        if (sweepFrequencySeconds != other.sweepFrequencySeconds) {
            return false;
        }
        if (writerIdleTimeSeconds != other.writerIdleTimeSeconds) {
            return false;
        }
        return true;
    }
}
