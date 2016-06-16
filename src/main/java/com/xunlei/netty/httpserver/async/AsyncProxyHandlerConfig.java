package com.xunlei.netty.httpserver.async;

import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.Config;

/**
 * @author 曾东
 * @since 2012-11-23 上午11:06:46
 * @param <T>
 */
@Service
public final class AsyncProxyHandlerConfig {

    private static AsyncProxyHandlerConfig instance;

    public static AsyncProxyHandlerConfig getInstance() {
        return instance;
    }

    @Config
    private long asyncTimeoutProcessorCheckMs = 200;// 默认200ms轮询一次(也就是 超时大概还有200ms的误差，如2s超时，其实是2s+200ms)
    @Config(resetable = true)
    private long asyncTimeoutProcessorMs = 2000; // 默认2秒就超时
    @Config
    private long asyncTimeoutTrimerCheckSec = 5;// 默认5s轮询一次
    @Config(resetable = true)
    private long asyncTimeoutTrimerSec = 10; // 默认10s就超时
    @Config(resetable = true)
    private int coreMapWarnSize = 5000;// 5000个，说明要报警
    @Autowired
    private HttpServerConfig httpServerConfig;

    public AsyncProxyHandlerConfig() {
        instance = this;
    }

    public long getAsyncTimeoutProcessorCheckMs() {
        return asyncTimeoutProcessorCheckMs;
    }

    public long getAsyncTimeoutProcessorMs() {
        return asyncTimeoutProcessorMs;
    }

    public ExecutorService getAsyncTimeoutProcessorThreadPool() {
        return httpServerConfig.getPipelineExecutorUnordered();
    }

    public long getAsyncTimeoutTrimerCheckSec() {
        return asyncTimeoutTrimerCheckSec;
    }

    public long getAsyncTimeoutTrimerSec() {
        return asyncTimeoutTrimerSec;
    }

    public int getCoreMapWarnSize() {
        return coreMapWarnSize;
    }
}
