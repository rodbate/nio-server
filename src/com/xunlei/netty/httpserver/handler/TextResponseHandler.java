package com.xunlei.netty.httpserver.handler;

import java.util.Collections;
import java.util.Set;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;

/**
 * @author ZengDong
 * @since 2011-3-18 下午06:19:53
 */
public abstract class TextResponseHandler implements Handler {

    public static final Logger log = Log.getLogger();
    @Autowired
    protected HttpServerConfig serverConfig;
    @Config
    protected String responseReturnNullInfo = "cmd return null";
    @Autowired
    protected TextResponseHandlerManager textResponseHandlerManager;
    /** 不计入日志的异常列表 */
    @Config(resetable = true, split = ",")
    protected Set<String> logThrowableIgnoreList = Collections.emptySet();

    /**
     * 处理当前的attach中的response具体包体
     * 
     * @param attach
     * @param cmdReturnObj
     * @return 非null 表示处理完成,null 表示不处理
     */
    public abstract String buildContentString(XLContextAttachment attach, Object cmdReturnObj);

    public abstract Object handleThrowable(XLContextAttachment attach, Throwable ex) throws Exception;

    public static void logError(final String mailTitleInfo, final String info, final Object... args) {
        // ConcurrentUtil.getDefaultExecutor().execute(new Runnable() {// 因为新版本1.0.0的logback发邮件时会自动在线程池上操作，所以不用外部来处理
        //
        // @Override
        // public void run() {
        Object objEx = args[args.length - 1];
        String exInfo = "";
        if (objEx instanceof Throwable) {
            exInfo = ((Throwable) objEx).getClass().getSimpleName();
        }
        MDC.put("mailTitle", exInfo + ": " + mailTitleInfo);
        log.error(info, args);
        // }
        // });
    }

    public void logThrowable(final XLContextAttachment attach, final XLHttpRequest request, XLHttpResponse response, final Throwable ex) {
        response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR); // 如果是未知错误则返回500
        if (logThrowableIgnoreList.contains(ex.getClass().getName())) { // 如果发现是无需报告的异常，则不进入发邮件流程
            return;
        }
        logError(request.getUrl(), "\n{}", request.getDetailInfo(), ex);
    }
}
