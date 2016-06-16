package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.exception.ClosedChannelError;
import com.xunlei.netty.httpserver.exception.FaultProcessorTimeoutError;
import com.xunlei.netty.httpserver.exception.ProcessFinishedError;
import com.xunlei.netty.httpserver.exception.TrimTimeoutError;
import com.xunlei.netty.httpserver.handler.TextResponseHandlerManager;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.HumanReadableUtil;
import com.xunlei.util.Log;
import com.xunlei.util.concurrent.BaseSchedulable;

/**
 * <pre>
 * AsyncProxyHandler 异步Proxy处理器
 * 一般用法：
 *   1.httpServer的cmd 业务层 获得连接Channel并发送请求Message
 *     asyncHandler.write(response.getAttach(),asyncCallback,actualServerAddr,message);
 * 
 *   2.asyncCallback 是异步获得消息后的回调接口
 *     messageReceived(ChannelHandlerContext ctx, MessageEvent e, XLContextAttachment attach)
 *     exceptionCaught(ChannelHandlerContext ctx, ChannelEvent e, XLContextAttachment attach)
 * 
 *     如果是要最终写回到httpServer，则是在此两方法上调用writeResponseToFront()
 * 
 * 
 * 通过client端的连接对象来回查callback,attach映射:(半双工)
 * AsyncProxyHandlerByChannelOneAddr       初始化时就指定好了actualServerAddr的唯一地址
 * AsyncProxyHandlerByChannelOneAddrPooled 初始化时就指定好了actualServerAddr的唯一地址,并使用了长连接及连接池
 * AsyncProxyHandlerByChannelPooled        使用了长连接及连接池，管理所有连接过的Channel
 * 
 * 通过发包回包中的sequence字段来回查callback,attach映射:(全双工)
 * AsyncProxyHandlerBySequence
 * 
 * @author ZengDong
 * @param <T>
 * @since 2011-10-7 下午04:03:02
 */
public abstract class AsyncProxyHandler<T> extends XLClientHandler {

    public static class AsyncCallbackAttach {

        protected AsyncCallback _callback;
        protected XLContextAttachment _attach;

        protected List<SequenceMessage> _messageList;
        protected List<SocketAddress> _addressList;
        protected long _messageSendTime;// 发送消息时间

        private List<SequenceMessage> messageTimeoutProcessEncountError;// 用于 后台定时处理器，不要对一个已经抛错的情况，继续轮询重执行(量少，且不用考虑效率问题，直接用list来存)

        public synchronized boolean isMessageNeedTimeoutProcess(SequenceMessage msg) {
            if (messageTimeoutProcessEncountError == null) {
                return true;
            }
            return !messageTimeoutProcessEncountError.contains(msg);
        }

        public synchronized void markMessageTimeoutProcessEncountError(SequenceMessage msg) {
            if (messageTimeoutProcessEncountError == null) {
                messageTimeoutProcessEncountError = new ArrayList<SequenceMessage>(1);
            }
            messageTimeoutProcessEncountError.add(msg);
        }

        protected AsyncCallbackAttach(XLContextAttachment attach) {
            this._attach = attach;
            attach.setAsyncCallbackAttach(this); // 互相引用
        }

        public AsyncCallbackAttach(XLContextAttachment attach, AsyncCallback callback) {
            this._callback = callback;
            this._attach = attach;
        }

        public AsyncCallback getCallback() {
            return _callback;
        }

        public XLContextAttachment getAttach() {
            return _attach;
        }

        public void messageSendPrepare(SocketAddress addr, SequenceMessage message) {
            if (this._messageList == null) {
                _messageList = new ArrayList<SequenceMessage>(1); // 默认认为就是 发一个请求
                _addressList = new ArrayList<SocketAddress>(1);
                _messageSendTime = System.currentTimeMillis();
            }
            _messageList.add(message);
            _addressList.add(addr);
        }

        // public SocketAddress getAddressBySeq(long seq) {
        // if (this._messageList != null) {
        // for (int i = 0; i < _messageList.size(); i++) {
        // SequenceMessage msg = _messageList.get(i);
        // if (msg.getSequence() == seq) {
        // return _addressList.get(i);
        // }
        // }
        // }
        // return null;
        // }

        public long getMessageSendTime() {
            return _messageSendTime;
        }

        public List<SequenceMessage> getMessageList() {
            return _messageList;
        }
    }

    public static NothingCallbackAttach NOTHING_CALLBACK_ATTACH = new NothingCallbackAttach();

    private static class NothingCallbackAttach extends AsyncCallbackAttach implements AsyncCallback {

        private static final Logger log = Log.getLogger();

        private NothingCallbackAttach() {
            super(new XLContextAttachment(null) {

                @Override
                public void initAsyncMessageEventQueue(int concurrentNum) {
                }

                @Override
                public void registerCloseable(Object obj) {
                }

                @Override
                public void registerCloseable(Thread t) {
                }

                @Override
                public void checkChannelOrThread() {
                }

                @Override
                public synchronized void unregisterCloseable(Object obj) {
                }

                @Override
                public synchronized void unregisterProcessThread() {
                }

                @Override
                public String getName() {
                    return "NothingCallbackAttach";
                }
            });
        }

        @Override
        public AsyncCallback getCallback() {
            return this;
        }

        @Override
        public void messageSendPrepare(SocketAddress addr, SequenceMessage message) {
            // nothing
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e, XLContextAttachment attach) throws Exception {
            // nothingCallBack
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e, XLContextAttachment attach) throws Exception {// TODO:
            log.error("ctx:{}", ctx, e);
        }
    }

    private static final Logger logger = Log.getLogger();
    public static final Object ASYNC_RESPONSE = new Object();// 用于标识当前请求会异步响应
    protected final Map<T, AsyncCallbackAttach> coreMap = new ConcurrentHashMap<T, AsyncCallbackAttach>();
    private long coreMapPurgeCount = 0;
    private String coreMapLastPurgeInfo = "";
    private AtomicInteger coreMapTimeoutCount = new AtomicInteger();
    private AtomicInteger coreMapTimeoutOkCount = new AtomicInteger();

    private DateStringUtil dsu_time = DateStringUtil.getInstance("HH:mm:ss");

    private void trimTimeoutAttach(long timeout) {
        StringBuilder info = new StringBuilder();
        int ori = coreMap.size();
        int count = 0;
        for (Map.Entry<T, AsyncCallbackAttach> e : coreMap.entrySet()) {
            AsyncCallbackAttach ac = e.getValue();
            T key = e.getKey();
            long span = System.currentTimeMillis() - ac.getMessageSendTime();
            if (span > timeout) {
                coreMap.remove(key);
                count++;
                if (e == NOTHING_CALLBACK_ATTACH) {// NOthingCallBackAttach 不用记录太多信息
                    continue;
                }
                coreMapPurgeCount++;

                SocketAddress addr = null;
                SequenceMessage msg = null;

                if (key instanceof Long) { // 这里为了最快速度找到 Addr，先这样写
                    if (ac._messageList != null) {
                        for (int i = 0; i < ac._messageList.size(); i++) {
                            SequenceMessage msgTmp = ac._messageList.get(i);
                            if (((Long) key).equals(msgTmp.getSequence())) {
                                addr = ac._addressList.get(i);
                                msg = msgTmp;
                                FaultProcessorUtil.recordFault(msgTmp, TrimTimeoutError.INSTANCE); // 能查到 msg的情况，才能记录到统一stat上
                                break;
                            }
                        }
                    }
                } else if (key instanceof Channel) {
                    addr = ((Channel) key).getRemoteAddress();
                }

                if (addr != null) {
                    AsyncStat as = getAsyncStat(addr);
                    as.timeoutCounter.incrementAndGet(); // 记录此stat超时
                    // as.asyncClientStat.record(System.currentTimeMillis(), ac.getMessageSendTime(), ac); // 不记录到整体
                    info.append(HumanReadableUtil.timeSpan(span)).append("\t").append(key).append("->").append(addr).append("\t").append(ac);
                } else {
                    info.append(HumanReadableUtil.timeSpan(span)).append("\t").append(key).append("->NA").append("\t").append(ac);
                }
                info.append("\n");
            }
        }
        int now = coreMap.size();

        if (now > config.getCoreMapWarnSize()) {
            coreMapLastPurgeInfo = "\tpurgeCount:[" + coreMapPurgeCount + "]\tlastPurge:[" + dsu_time.now() + "]";
            log.error("!!WARNING!! -- count:{},ori:{}->now:{},purgeCount:{},timeout:{},detail:\n{}", new Object[] {
                count,
                ori,
                now,
                coreMapPurgeCount,
                timeout,
                info
            });
        } else if (count > 0) {
            coreMapLastPurgeInfo = "\tpurgeCount:[" + coreMapPurgeCount + "]\tlastPurge:[" + dsu_time.now() + "]";
            log.debug("count:{},ori:{}->now:{},purgeCount:{},timeout:{},detail:\n{}", new Object[] {
                count,
                ori,
                now,
                coreMapPurgeCount,
                timeout,
                info
            });
        }
    }

    private BaseSchedulable timeoutTrimer = new BaseSchedulable() {

        @Override
        public void process() throws Throwable {
            trimTimeoutAttach(config.getAsyncTimeoutTrimerSec() * 1000);
        }
    };
    protected final Logger logTimeoutProcessor = Log.getLoggerWithSuffix("timeoutProcessor");

    private void processTimeoutAttach(long timeoutMs) {// 只是对于 bySeq 的方式进行了使用，byChannel没有测试
        for (Map.Entry<T, AsyncCallbackAttach> e : coreMap.entrySet()) {
            final AsyncCallbackAttach v = e.getValue();
            if (e == NOTHING_CALLBACK_ATTACH) {
                continue;
            }
            final T key = e.getKey();
            final long span = System.currentTimeMillis() - v.getMessageSendTime();
            if (key instanceof Long && v._messageList != null && span > timeoutMs) {
                for (int i = 0; i < v._messageList.size(); i++) {
                    final SequenceMessage msg = v._messageList.get(i);
                    final SocketAddress addr = v._addressList.get(i);
                    if (((Long) key).equals(msg.getSequence())) {
                        final FaultProcessorTimeoutError error = FaultProcessorTimeoutError.INSTANCE;
                        if (coreMap.containsKey(msg.getSequence()) && FaultProcessorUtil.needDealWithFault(msg, v, error) && v.isMessageNeedTimeoutProcess(msg)) {// 说明已经超时了，并且是需要处理的，并且以前没有处理过的
                            config.getAsyncTimeoutProcessorThreadPool().execute(new Runnable() {// 弄一个另一个线程来跑下面的业务

                                        @Override
                                        public void run() {
                                            // 如果用containsKey的方式来判断的话，因为没有同步块，还是有极小的概率 出现同时的情况，因此这里改成 先remove出现问题了再put回来的方式
                                            if (coreMap.remove(msg.getSequence()) != null && v.isMessageNeedTimeoutProcess(msg)) { // 再次检查的原因是:防止这时真的有回包了
                                                try {
                                                    coreMapTimeoutCount.incrementAndGet();
                                                    FaultProcessorUtil.dealWithFault(msg, v, error);
                                                    coreMapTimeoutOkCount.incrementAndGet();
                                                    logTimeoutProcessor.info(" OK:{}MS\t{}\t>> {}\t{}", new Object[] {
                                                        span,
                                                        v.getAttach(),
                                                        addr,
                                                        msg
                                                    });
                                                } catch (FaultProcessorTimeoutError e2) {// （不可能报此错，因为前面已经判断needDealWithFault了） 说明这个超时了，但是 是主体请求，没有超时异常处理，最多只能打日志警告
                                                } catch (Throwable e2) {// FaultProcessorError:说明这个内部messageReceived出问题了 还有其他未知问题
                                                    coreMap.put(key, v);
                                                    v.markMessageTimeoutProcessEncountError(msg);
                                                    logTimeoutProcessor.error("ERR:{}MS\t{}\t>> {}\t{}", new Object[] {
                                                        span,
                                                        v.getAttach(),
                                                        addr,
                                                        msg,
                                                        e2
                                                    });
                                                }
                                            }

                                        }
                                    });

                        } else {// 说明已经及时收到回包了，不用管
                        }
                    }
                }
            }
        }
    }

    private TimeoutSchedulable timeoutProcessor = new TimeoutSchedulable();

    private class TimeoutSchedulable extends BaseSchedulable {

        public TimeoutSchedulable() {
            setLogEnable(false);
        }

        @Override
        public void process() throws Throwable {
            processTimeoutAttach(config.getAsyncTimeoutProcessorMs());
        }
    }

    /**
     * 回写响应到前端
     */
    public static void writeResponseToFront(TextResponseHandlerManager localHttpServerResponseHandlerManager, XLContextAttachment attach, Object cmdReturnObj) {
        try {
            localHttpServerResponseHandlerManager.writeResponse(attach, cmdReturnObj);
        } catch (ClosedChannelError e) {
            logger.error("channelClosed    :{}", attach.getChannelHandlerContext().getChannel());
        }
    }

    private AsyncProxyHandlerConfig config = AsyncProxyHandlerConfig.getInstance();

    public AsyncProxyHandler(ClientBootstrap backstageClientBootstrap, String name, ConcurrentHashMap<SocketAddress, AsyncStat> addressStatMap) {
        super(backstageClientBootstrap, name, addressStatMap);
        timeoutTrimer.scheduleWithFixedDelaySec(config.getAsyncTimeoutTrimerCheckSec());
        timeoutProcessor.scheduleWithFixedDelayMs(config.getAsyncTimeoutProcessorCheckMs());
    }

    public AsyncProxyHandler(ClientBootstrap backstageClientBootstrap, String name) {
        super(backstageClientBootstrap, name);
        timeoutTrimer.scheduleWithFixedDelaySec(config.getAsyncTimeoutTrimerCheckSec());
        timeoutProcessor.scheduleWithFixedDelayMs(config.getAsyncTimeoutProcessorCheckMs());
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e, AsyncCallbackAttach ca) throws Exception {
        ca.getCallback().exceptionCaught(ctx, e, ca.getAttach());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.messageReceived(ctx, e);
        Channel c = ctx.getChannel();
        AsyncCallbackAttach ca = pollAsyncCallbackAttach(ctx, e);
        XLContextAttachment attach = null;
        if (ca == null) {
            log.error("cannot find AsyncCallbackAttach when messageReceived,client_channel:{},message:{}", c, e.getMessage());
            return;
        }
        messageRecvStat(c.getRemoteAddress(), ca);

        attach = ca.getAttach();
        attach.checkChannelOrThread();
        // if (!attach.getChannelHandlerContext().getChannel().isOpen()) {//因为可能在外部wirteResponse还报错，所以还是用默认的 check
        // log.info("channelClosed     {}:{}", new Object[] { "asyncProxy", attach });
        // return;
        // }
        attach.unregisterCloseable(c);// 取消注册，这个channel已经走了一遍 发送请求，接收请求的工作了

        AsyncCallback callback = ca.getCallback();
        Thread t = Thread.currentThread();
        attach.registerCloseable(t);

        try {
            callback.messageReceived(ctx, e, attach);
        } catch (ProcessFinishedError e1) { // 处理流程结束标志，不当作异常处理
        } catch (Throwable e2) {// 这里捕获的异常都是 正常解码了回包后业务处理出问题的情况，不用关闭此channel
            exceptionCaught(ctx, e2, ca);
        } finally {
            attach.unregisterCloseable(t);
            messageReceivedFinally(ctx, e);
        }
    }

    /**
     * <pre>
     * 接收到后台服务器消息后，业务处理完成的后缀操作
     * 如果是 短连接，则是关闭连接
     * 如果是 使用连接池做的长连接，则是回收连接
     */
    protected void messageReceivedFinally(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    }

    public abstract AsyncCallbackAttach pollAsyncCallbackAttach(ChannelHandlerContext ctx, MessageEvent e);

    /**
     * 获得统计信息
     */
    public StringBuilder printStatInfo(Map<Long, AsyncCallbackAttach> seqMap) {
        StringBuilder sb = new StringBuilder();

        sb.append("processingAttach:[").append(coreMap.size()).append("]")
                .append(coreMapTimeoutCount.get() > 0 ? "timeoutCount:[" + coreMapTimeoutOkCount.get() + "/" + coreMapTimeoutCount.get() + "]" : "")
                .append(coreMapPurgeCount > 0 ? coreMapLastPurgeInfo : "").append("\n");
        long now = System.currentTimeMillis();
        Set<AsyncCallbackAttach> values = new HashSet<AsyncCallbackAttach>();
        List<T> nothingCallbackList = new ArrayList<T>(0);
        for (Map.Entry<T, AsyncCallbackAttach> e : coreMap.entrySet()) {
            T key = e.getKey();
            AsyncCallbackAttach value = e.getValue();
            if (value == NOTHING_CALLBACK_ATTACH) {
                nothingCallbackList.add(key);
            } else {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            sb.append("\n");
        }
        for (AsyncCallbackAttach v : values) {
            sb.append(v.getAttach());
            if (v._messageList == null) {
                sb.append("\n");
            } else {
                sb.append("\t").append(HumanReadableUtil.timeSpan(now - v._messageSendTime)).append("\n");
                for (int i = 0; i < v._messageList.size(); i++) {
                    SequenceMessage msg = v._messageList.get(i);
                    SocketAddress addr = v._addressList.get(i);
                    String arrow = seqMap.containsKey(msg.getSequence()) ? "*> " : "=> ";
                    sb.append(arrow).append(addr).append("\t").append(msg).append("\n");
                }
                sb.append("\n");
            }
        }

        if (!nothingCallbackList.isEmpty()) {
            sb.append("----------------------------------------------------\n");
            sb.append("nothingCallbackAttach:[").append(nothingCallbackList.size()).append("]\n");
            for (T key : nothingCallbackList) {
                sb.append(key).append("\n");
            }
        }
        sb.append("----------------------------------------------------\n");
        sb.append(super.printStatInfo());
        return sb;
    }

    /**
     * 获得统计信息
     */
    @Override
    public StringBuilder printStatInfo() {
        Map<Long, AsyncCallbackAttach> seqMap = Collections.emptyMap();
        return printStatInfo(seqMap);
    }

    /**
     * 提交2个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 2;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
    }

    /**
     * 提交3个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 3;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
    }

    /**
     * 提交4个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 4;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
    }

    /**
     * 提交5个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 5;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
    }

    /**
     * 提交6个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, SocketAddress address6, SequenceMessage msg6, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 6;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        if (msg6 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
        submit(address6, msg6, ca);
    }

    /**
     * 提交7个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, SocketAddress address6, SequenceMessage msg6, SocketAddress address7, SequenceMessage msg7, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 7;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        if (msg6 == null) {
            num--;
        }
        if (msg7 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
        submit(address6, msg6, ca);
        submit(address7, msg7, ca);
    }

    /**
     * 提交8个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, SocketAddress address6, SequenceMessage msg6, SocketAddress address7, SequenceMessage msg7, SocketAddress address8,
            SequenceMessage msg8, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 8;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        if (msg6 == null) {
            num--;
        }
        if (msg7 == null) {
            num--;
        }
        if (msg8 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
        submit(address6, msg6, ca);
        submit(address7, msg7, ca);
        submit(address8, msg8, ca);
    }

    /**
     * 提交9个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, SocketAddress address6, SequenceMessage msg6, SocketAddress address7, SequenceMessage msg7, SocketAddress address8,
            SequenceMessage msg8, SocketAddress address9, SequenceMessage msg9, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 9;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        if (msg6 == null) {
            num--;
        }
        if (msg7 == null) {
            num--;
        }
        if (msg8 == null) {
            num--;
        }
        if (msg9 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
        submit(address6, msg6, ca);
        submit(address7, msg7, ca);
        submit(address8, msg8, ca);
        submit(address9, msg9, ca);
    }

    /**
     * 提交10个异步请求
     */
    public void submit(SocketAddress address1, SequenceMessage msg1, SocketAddress address2, SequenceMessage msg2, SocketAddress address3, SequenceMessage msg3, SocketAddress address4,
            SequenceMessage msg4, SocketAddress address5, SequenceMessage msg5, SocketAddress address6, SequenceMessage msg6, SocketAddress address7, SequenceMessage msg7, SocketAddress address8,
            SequenceMessage msg8, SocketAddress address9, SequenceMessage msg9, SocketAddress address10, SequenceMessage msg10, AsyncCallbackAttach ca) {
        XLContextAttachment attach = ca.getAttach();
        int num = 10;
        if (msg1 == null) {
            num--;
        }
        if (msg2 == null) {
            num--;
        }
        if (msg3 == null) {
            num--;
        }
        if (msg4 == null) {
            num--;
        }
        if (msg5 == null) {
            num--;
        }
        if (msg6 == null) {
            num--;
        }
        if (msg7 == null) {
            num--;
        }
        if (msg8 == null) {
            num--;
        }
        if (msg9 == null) {
            num--;
        }
        if (msg10 == null) {
            num--;
        }
        attach.initAsyncMessageEventQueue(num);
        submit(address1, msg1, ca);
        submit(address2, msg2, ca);
        submit(address3, msg3, ca);
        submit(address4, msg4, ca);
        submit(address5, msg5, ca);
        submit(address6, msg6, ca);
        submit(address7, msg7, ca);
        submit(address8, msg8, ca);
        submit(address9, msg9, ca);
        submit(address9, msg10, ca);
    }

    /**
     * 请改用submit(SocketAddress backstageHostAddress, SequenceMessage msg, AsyncCallbackAttach ca)
     */
    @Deprecated
    public void submit(SocketAddress backstageHostAddress, SequenceMessage msg, XLContextAttachment attach, AsyncCallbackAttach asyncCallbackAttach) {
        submit(backstageHostAddress, msg, asyncCallbackAttach);
    }

    /**
     * 严重注意，这里要传的AsyncCallback是单例，而且不是 AsyncCallbackAttach提交异步请求
     */
    public void submit(SocketAddress backstageHostAddress, SequenceMessage msg, XLContextAttachment attach, AsyncCallback asyncCallback) {
        attach.checkChannelOrThread();
        if (asyncCallback instanceof AsyncCallbackAttach) {
            submit(backstageHostAddress, msg, (AsyncCallbackAttach) asyncCallback);
        } else {
            submit(backstageHostAddress, msg, new AsyncCallbackAttach(attach, asyncCallback));
        }
    }

    /**
     * 发送一个不需要处理回包的请求
     */
    public void submit(SocketAddress backstageHostAddress, SequenceMessage msg) {
        submit(backstageHostAddress, msg, NOTHING_CALLBACK_ATTACH);
    }

    public abstract void submit(SocketAddress backstageHostAddress, SequenceMessage msg, AsyncCallbackAttach ca);
}
