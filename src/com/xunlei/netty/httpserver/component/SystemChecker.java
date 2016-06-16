package com.xunlei.netty.httpserver.component;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.AfterBootstrap;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;
import com.xunlei.util.SystemInfo;
import com.xunlei.util.SystemMonitor;
import com.xunlei.util.concurrent.ConcurrentUtil;

@Service
public class SystemChecker {

    private static final Logger log = Log.getLogger(SystemMonitor.class);
    @Autowired
    protected HttpServerConfig config;
    private static volatile boolean denialOfService = false;
    /** 系统最重要的线程池的监控，要很实时 */
    @Config
    private int dosMonitorCheckSec = 1;
    /** 垃圾回收监控，建议5分钟监控一次（因为是总回收时间的对比） */
    @Config
    private int gcMonitorSec = 5 * 60;
    private static volatile boolean lastTestDenialOfService = false;
    @Config(resetable = true)
    private double loadMonitorErrorThreshold = ConcurrentUtil.CORE_PROCESSOR_NUM;
    /** 系统负载监控，建议半分钟监控一次（因为是平均负载） */
    @Config
    private int loadMonitorSec = 1 * 30;
    /** 数值的界定，请参考http://blog.csdn.net/marising/article/details/5182771 */
    @Config(resetable = true)
    private double loadMonitorWarnThreshold = ConcurrentUtil.CORE_PROCESSOR_NUM * 0.7;
    /** 线程cpu时间监控,建议每半分钟监控一次 */
    @Config
    private int threadCpuTimeMonitorSec = 1 * 30;
    @Config(resetable = true)
    private int threadMonitorQueueThreshold = 1000;
    /** 线程池监控，建议每5秒钟监控一次（因为每时每刻的queue的数量都可能在大幅度变化） */
    @Config
    private int threadMonitorSec = 5;
    /** log线程池监控，要严防队列过大导致IO过高和内存溢出 */
    @Config
    private int logMonitorCheckSec = 10;
    @Config(resetable = true)
    private int logMonitorLimit = 20000;
    private static volatile boolean lastTestLog = false;
    private static volatile boolean logEnabled = true;
    /** 线程池允许等待的线程数 */
    @Config(resetable = true)
    private int threadPoolQueueCountLimit = 30000;

    @AfterBootstrap
    protected void init() {
        SystemMonitor.initGarbageCollectMonitor(gcMonitorSec);
        SystemMonitor.initLoadAverageMonitor(loadMonitorSec, loadMonitorWarnThreshold, loadMonitorErrorThreshold);
        SystemMonitor.initThreadCpuTimeMonitor(threadCpuTimeMonitorSec);
        SystemMonitor.initThreadPoolMonitor(threadMonitorSec, threadMonitorQueueThreshold);
        initDenialOfServiceMonitor();
        initLogMonitor();
    }

    public void initDenialOfServiceMonitor() {
        if (dosMonitorCheckSec > 0) {
            log.info("DenialOfServiceMonitor ON, interval:{}sec, pipelineSize:{}", dosMonitorCheckSec, config.getPipelineExecutorOrdered().getCorePoolSize());
            ConcurrentUtil.getWatchdog().scheduleWithFixedDelay(new Runnable() {

                private boolean check(ThreadPoolExecutor executor) {
                    int activeCount = executor.getActiveCount();
                    int queueCount = executor.getQueue().size(); // 等待队列的大小
                    int largestPoolSize = executor.getLargestPoolSize();
                    int atLeastReamin = largestPoolSize <= HttpServerConfig.CORE_PROCESSOR_NUM ? 1 : HttpServerConfig.CORE_PROCESSOR_NUM; // 最少要剩余的空闲线程数
                    if (activeCount > 0 && activeCount + atLeastReamin >= largestPoolSize) {
                        String pipelineInfo = SystemInfo.getThreadsDetailInfo("PIPELINE", true, 20); // 先打印好
                        ConcurrentUtil.threadSleep(2000); // 小小暂停一下，防止是那种突然一下子冲过来的情况而报DOS
                        activeCount = executor.getActiveCount();
                        int currQueueCount = executor.getQueue().size(); // 等待队列的大小，如果持续增大，就说明肯定有问题，需要DOS
                        if (activeCount > 0 && activeCount + atLeastReamin >= largestPoolSize && currQueueCount > queueCount && currQueueCount > threadPoolQueueCountLimit) { // 打印好了，线程池仍然大量占用，就发出报警邮件
                            if (!lastTestDenialOfService) {
                                MDC.put("mailTitle", "DenialOfService");
                                log.error("DENIAL OF SERVICE, ACTIVE_SIZE:{}, WAITTING_QUEUE_SIZE:{}, RUNNING PIPELINE:\n{}\n\n", activeCount, currQueueCount, pipelineInfo);
                            }
                            denialOfService = true;
                            lastTestDenialOfService = true;
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public void run() {
                    boolean r = check(config.getPipelineExecutorOrdered());
                    boolean r1 = check(config.getPipelineExecutorUnordered());
                    if (!r && !r1) {
                        denialOfService = false;
                        lastTestDenialOfService = false;
                    }
                }
            }, dosMonitorCheckSec * 5, dosMonitorCheckSec, TimeUnit.SECONDS);// httpServer刚启动时，很有可能很多请求冲进来，先不拒绝服务，所以暂定5s后再开始定时
        }
    }

    public void initLogMonitor() {
        if (logMonitorCheckSec > 0) {
            final ThreadPoolExecutor logExecutor = (ThreadPoolExecutor) ConcurrentUtil.getLogExecutor();
            log.info("LogMonitor ON, interval:{}sec, logExecutorSize:{}", logMonitorCheckSec, logExecutor.getQueue().size());
            ConcurrentUtil.getWatchdog().scheduleWithFixedDelay(new Runnable() {

                private boolean check(ThreadPoolExecutor executor) {
                    int queueCount = executor.getQueue().size(); // 如果队列的log超过限制，就临时把日志功能关闭
                    if (queueCount > logMonitorLimit) {
                        if (!lastTestLog) {
                            MDC.put("mailTitle", "TOO MANY LOGS");
                            log.error("TOO MANY LOGS,WAITTING_QUEUE_SIZE:{}\n", queueCount);
                        }
                        logEnabled = false;
                        lastTestLog = true;
                        return true;
                    }
                    return false;
                }

                @Override
                public void run() {
                    boolean r = check(logExecutor);
                    if (!r) {
                        logEnabled = true;
                        lastTestLog = false;
                    }
                }
            }, logMonitorCheckSec * 12, logMonitorCheckSec, TimeUnit.SECONDS);
        }
    }

    public static boolean isDenialOfService() {
        return denialOfService;
    }

    /**
     * 如果日志功能打开了，才记录日志
     */
    public static boolean isLogEnabled() {
        return logEnabled;
    }
}
