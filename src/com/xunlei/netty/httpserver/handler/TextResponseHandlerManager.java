package com.xunlei.netty.httpserver.handler;

import java.lang.reflect.InvocationTargetException;
import javax.annotation.Resource;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.exception.ProcessFinishedError;
import com.xunlei.netty.httpserver.exception.ProcessTimeoutError;
import com.xunlei.netty.httpserver.exception.ResourceNotFoundError;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.AfterConfig;

/**
 * @author ZengDong
 * @since 2010-3-25 下午01:12:16
 */
@Service
public class TextResponseHandlerManager extends HandlerManager<TextResponseHandler> {

    @Autowired
    private HttpServerConfig serverConfig;
    @Resource
    private PlainHandler plainHandler;
    @Resource
    private PlainHandlerExt plainHandlerExt;

    @AfterConfig
    public void initHandlerChain() {// 默认加载plain跟html处理器
        addFirst(plainHandlerExt);
        addLast(plainHandler);
    }

    public String getContentString(XLContextAttachment attach, Object cmdReturnObj) {
        // 遍历所有已经注册好的 文本内容处理器
        for (TextResponseHandler trh : getHandlerChain()) {
            String contentStr = trh.buildContentString(attach, cmdReturnObj);// 如果有返回内容,则设置resp的content,并退出
            if (contentStr != null) {
                return contentStr;
            }
        }
        return null;
    }

    private void setContent(XLContextAttachment attach, Object cmdReturnObj) {
        XLHttpResponse response = attach.getResponse();
        if (!response.isContentSetted()) {
            String contentStr = null;

            // 遍历所有已经注册好的 文本内容处理器
            for (TextResponseHandler trh : getHandlerChain()) {
                contentStr = trh.buildContentString(attach, cmdReturnObj);// 如果有返回内容,则设置resp的content,并退出
                if (contentStr != null) {
                    response.setContentString(contentStr);
                    return;
                }
            }
        }
    }

    // public void writeTimeoutResponse(Channel channel) {
    // XLHttpResponse response = new XLHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
    // Checkers.checkChannelOpen(channel);
    // response.setContent(ChannelBuffers.copiedBuffer("process timeout", CharsetUtil.UTF_8));
    // boolean close = setHeader(null, response);
    //
    // // statistics.writeBegin(attach); // TODO:不统计编码用时
    // ChannelFuture future = channel.write(response);
    // if (close) {
    // future.addListener(ChannelFutureListener.CLOSE);
    // }
    // }

    public void writeResponse(XLContextAttachment attach, Object cmdReturnObj) {
        try {
            Channel channel = attach.getChannelHandlerContext().getChannel();
            attach.checkChannelOrThread();
            XLHttpResponse response = attach.getResponse();
            XLHttpRequest request = attach.getRequest();

            serverConfig.getStatistics().writeBegin(attach);
            setContent(attach, cmdReturnObj);
            response.packagingCookies();
            attach.setKeepAliveHeader();

            // Write the response.
            // Checkers.checkChannelOpen(channel);
            ChannelFuture future = channel.write(response);
            // Close the connection after the write operation is done if necessary.
            future.addListener(attach);

            // TODO:暂时只在 有完整响应时,才写到accessLog中
            // TODO:如果在响应编码时出错,是否写到accessLog中呢
            serverConfig.getAccessLog().log(request, response);
        } finally {
            // attach.close(); //bug 会把async的channel关了
            // TODO:这里面想 在写resp时自动关闭coloseable,必须重新设计closeable为两类：
            // 一类是 可以正常关闭的，如业务处理完后，需要关闭的Io
            // 另一类是 在异常出现时，才要关闭的，如异步client Proxy 保存的 channel，这个在正常情况在 连接池情况下应该是不close的，只在异常时close
        }
    }

    // private static final Logger log = Log.getLogger();

    /**
     * 处理业务中处理错误
     */
    public Object handleThrowable(XLContextAttachment attach, Throwable e) throws Exception {
        Object cmdReturnObj = null;
        Throwable ex = e;
        // 1.先确定真实的异常
        if (ex instanceof NoSuchMethodException || ex instanceof SecurityException) {
            ex = ResourceNotFoundError.INSTANCE;
        } else {
            if (ex instanceof InvocationTargetException) {
                ex = ((InvocationTargetException) ex).getTargetException();
                // 此异常代表处理流程已经结束了，无需进行剩余的操作。回写response等操作在抛出此异常前的代码都有处理，不必要再调用dispatch的finally部分，否则会有网络异常
                if (ex instanceof ProcessFinishedError) {
                    return AsyncProxyHandler.ASYNC_RESPONSE;
                }
            } else if (ex instanceof InterruptedException) {
                ex = ProcessTimeoutError.INSTANCE;
            }
        }
        attach.registerThrowable(ex);// 记录到 attach中
        // 2.使用异常处理链来处理
        for (TextResponseHandler th : getHandlerChain()) {
            cmdReturnObj = th.handleThrowable(attach, ex);
            if (cmdReturnObj != null) {
                return cmdReturnObj;
            }
        }
        return cmdReturnObj;
    }
}
