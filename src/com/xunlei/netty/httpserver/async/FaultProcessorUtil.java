package com.xunlei.netty.httpserver.async;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler.AsyncCallbackAttach;
import com.xunlei.netty.httpserver.exception.ConnectToServerFailedError;
import com.xunlei.netty.httpserver.exception.FaultProcessorError;
import com.xunlei.netty.httpserver.exception.FaultProcessorTimeoutError;
import com.xunlei.netty.httpserver.exception.ProcessFinishedError;
import com.xunlei.netty.httpserver.exception.TrimTimeoutError;
import com.xunlei.spring.AfterConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;
import com.xunlei.util.StringTools;
import com.xunlei.util.SystemMonitor;
import com.xunlei.util.concurrent.BaseSchedulable;

/**
 * 当异步发送消息失败，或回包失败时，为了容错而进行的退化处理
 * 
 * @since 2012-11-21
 * @author hujiachao
 */
@Service
public final class FaultProcessorUtil {

    public interface SequenceMessageCategory {

        public Object getMessageCategory(SequenceMessage msg);
    }

    /**
     * <pre>
     * 当异步发送消息失败，或回包失败时，为了容错而进行的退化处理
     * 具体使用时，请实现该类，并加注@Service注解，配置进Spring的扫描路径
     * FaultProcessor会自动加载之
     * </pre>
     * 
     * @since 2012-11-21
     * @author hujiachao
     */
    public interface FaultProcessor {

        /**
         * @param reqMsg 请求消息体
         * @param ca attach
         * @param ex 当前异常:如连接失败，请求超时
         * @return 响应消息体
         */
        public Object getFaultRespMessage(SequenceMessage reqMsg, AsyncCallbackAttach ca, Exception ex);

        /**
         * 记录请求的消息，用于统计失败率
         */
        public void recordReq(SequenceMessage reqMsg);
    }

    /**
     * 用于伪造返回包的MessageEvent，由于处理回包时只取消息体，不涉及其他东西，故其它都可以返回null
     */
    public static class FaultMessageEvent implements MessageEvent {

        private Object obj;

        public FaultMessageEvent(Object obj) {
            this.obj = obj;
        }

        @Override
        public Channel getChannel() {
            return null;
        }

        @Override
        public ChannelFuture getFuture() {
            return null;
        }

        @Override
        public Object getMessage() {
            return obj;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }
    }

    private static FaultProcessorUtil INSTANCE;

    private FaultProcessorUtil() {
        INSTANCE = this;
    }

    public static FaultProcessor getFaultProcessor() {
        return INSTANCE.faultProcessor;
    }

    @Autowired(required = false)
    private FaultProcessor faultProcessor = new FaultProcessor() {

        @Override
        public Object getFaultRespMessage(SequenceMessage reqMsg, AsyncCallbackAttach ca, Exception ex) {
            return null;
        }

        @Override
        public void recordReq(SequenceMessage reqMsg) {
        }
    };

    @Autowired(required = false)
    private SequenceMessageCategory sequenceMessageCategory = new SequenceMessageCategory() {

        @Override
        public Object getMessageCategory(SequenceMessage msg) {
            return null;
        }
    };

    public static boolean needDealWithFault(SequenceMessage reqMsg, AsyncCallbackAttach ca, RuntimeException ex) {
        Object respObj = INSTANCE.faultProcessor.getFaultRespMessage(reqMsg, ca, ex);
        return respObj != null;
    }

    /**
     * 退化处理规则 注意：因为messageReceived内部是要走业务逻辑，因此调用此方法的线程必须是 业务线程，不然会很慢
     */
    public static void dealWithFault(SequenceMessage reqMsg, AsyncCallbackAttach ca, RuntimeException ex) {
        recordFault(reqMsg, ex);
        Object respObj = INSTANCE.faultProcessor.getFaultRespMessage(reqMsg, ca, ex);
        if (respObj != null) {
            MessageEvent event = new FaultMessageEvent(respObj);
            try {
                ca.getCallback().messageReceived(ca.getAttach().getChannelHandlerContext(), event, ca.getAttach());
            } catch (ProcessFinishedError e) {// 此异常是正常流程
            } catch (Throwable e) {
                FaultStat fs = getFaultStat(reqMsg);
                if (fs != null) {
                    fs.faultErr();
                }
                throw new FaultProcessorError("dealWithFault:" + ex, e);
            }
        } else {
            throw ex;
        }
    }

    // --------------------------------------------------------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------------------------------------------------------
    // 以下为 统计相关

    public static String getFaultStatFmt() {
        return "%-50s %-10s %-19s %-10s %-10s %-10s %-10s %-10s\n";
    }

    /**
     * 一个seqMessage的错误统计
     */
    public static class FaultStat {

        private String category;
        private AtomicLong submit = new AtomicLong(); // 请求数
        private AtomicLong timeout = new AtomicLong(); // 超时退化处理
        private AtomicLong faultErr = new AtomicLong(); // 超时退化处理失败
        private AtomicLong cantConnect = new AtomicLong(); // 连接不上
        private AtomicLong trim = new AtomicLong(); // 超时舍弃

        public FaultStat(Object category) {
            if (category instanceof Class<?>) {
                this.category = ((Class<?>) category).getSimpleName();
            } else {
                this.category = category.toString();
            }
        }

        /**
         * 提交请求的时候，请求数+1
         */
        public void recordReq() {
            submit.incrementAndGet();
        }

        public void record(RuntimeException ex) {
            if (ex instanceof FaultProcessorTimeoutError) {
                timeout.incrementAndGet();
            } else if (ex instanceof ConnectToServerFailedError) {
                cantConnect.incrementAndGet();
            } else if (ex instanceof TrimTimeoutError) {
                trim.incrementAndGet();
            }
        }

        public void add(FaultStat fs) {
            submit.addAndGet(fs.submit.get());
            timeout.addAndGet(fs.timeout.get());
            faultErr.addAndGet(fs.faultErr.get());
            cantConnect.addAndGet(fs.cantConnect.get());
            trim.addAndGet(fs.trim.get());
        }

        public long sum() {
            return timeout.get() + faultErr.get() + cantConnect.get() + trim.get();
        }

        private String getNumString(AtomicLong i) {
            long a = i.get();
            return a == 0 ? "-" : a + "";
        }

        public void trim() {
            trim.incrementAndGet();
        }

        public void faultErr() {
            faultErr.incrementAndGet();
        }

        /**
         * 获取请求失败率（总体的），范围从0-100
         */
        public float getErrorRate() {
            long sub = submit.get();
            return sub > 0 ? 100f * sum() / sub : 0;
        }

        @Override
        public String toString() {
            return String.format(getFaultStatFmt(), category, sum(), getNumString(submit), String.format("%.3f%%", getErrorRate()), getNumString(timeout), getNumString(cantConnect),
                    getNumString(trim), getNumString(faultErr));
        }
    }

    private static FaultStat faultSum = new FaultStat("faultSum");
    private static FaultStat faultSumHistory = new FaultStat("faultSumHistory");
    private static Map<Object, FaultStat> faultStatMap = new ConcurrentHashMap<Object, FaultStat>();

    private static void resetFaultStat() {
        faultSumHistory.add(faultSum);
        faultSum = new FaultStat("faultSum");
        faultStatMap.clear();
    }

    /**
     * 输出处理失败的详情
     */
    public static String getFaultStatInfo() {
        StringBuilder info = new StringBuilder("FaultStatInfo:\n");
        // 分类名称，总失败数，总提交请求数，失败率，超时数，连接错误数，超时清理数，容错处理失败数
        info.append(String.format(getFaultStatFmt(), "CATEGORY", "ALLERR", "SUBMIT", "ERRRATE", "TIMEOUT", "CONNERR", "TRIM", "FAULTERR"));
        boolean hasError = false;
        long submitSum = 0;
        for (FaultStat fs : faultStatMap.values()) {
            if (fs.sum() > 0) { // 有错误的才显示出来
                hasError = true;
                info.append(fs);
                submitSum += fs.submit.get();
            }
        }
        if (hasError) {
            faultSum.submit.set(submitSum);
            info.append(faultSum);
            info.append(faultSumHistory);
            return info.toString();
        }
        return "";
    }

    /**
     * !!!! 注意有可能返回null
     */
    public static FaultStat getFaultStat(SequenceMessage msg) {
        Object category = INSTANCE.sequenceMessageCategory.getMessageCategory(msg);
        if (category != null) {
            FaultStat fs = getFaultStat(category);
            return fs;
        }
        return null;
    }

    private static FaultStat getFaultStat(Object catetory) {
        FaultStat fs = faultStatMap.get(catetory);
        if (fs == null) {
            synchronized (faultStatMap) {
                fs = faultStatMap.get(catetory);
                if (fs == null) {
                    fs = new FaultStat(catetory);
                    faultStatMap.put(catetory, fs);
                }
            }
        }
        return fs;
    }

    public static void recordFault(SequenceMessage msg, RuntimeException ex) {
        // faultSum.record(ex); // 找得到分类时才记录
        FaultStat fs = getFaultStat(msg);
        if (fs != null) {
            faultSum.record(ex);
            fs.record(ex);
        }
    }

    @Config(resetable = true)
    private float faultStatWarningRate = 20; // 在5分钟统计范围内，如果失败率超过一定数目，就要报邮件
    @Config(resetable = true)
    private int faultStatWarningNum = 50; // 在5分钟统计范围内，如果失败个数超过一定数目，就需要报邮件
    @Config(resetable = true)
    private int faultStatCheckSec = 5 * 60; // 5分钟统计一次

    @AfterConfig
    private void initSche() {
        faultStatSchedulaber.scheduleWithFixedDelaySec(faultStatCheckSec);
    }

    private static final Logger log_monitor = Log.getLogger(SystemMonitor.class);
    private BaseSchedulable faultStatSchedulaber = new BaseSchedulable() {

        @Override
        public void process() throws Throwable {
            boolean needAlarm = false;
            for (FaultStat fs : faultStatMap.values()) {
                if (fs.getErrorRate() > faultStatWarningRate && fs.sum() > faultStatWarningNum) { // 满足条件，就需要发邮件报警
                    needAlarm = true;
                    break;
                }
            }
            if (needAlarm) {
                MDC.put("mailTitle", "FaultProcessorError,sum:" + faultSum.sum());
                log_monitor.error(getFaultStatInfo());
            } else {
                String info = getFaultStatInfo();
                if (StringTools.isNotEmpty(info)) {
                    log_monitor.info(info);
                }
            }
            resetFaultStat();
        }
    };
}
