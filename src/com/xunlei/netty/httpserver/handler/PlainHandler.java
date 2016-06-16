package com.xunlei.netty.httpserver.handler;

import java.util.List;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.exception.AbstractHttpServerError;
import com.xunlei.util.StringHelper;

/**
 * @author ZengDong
 * @since 2011-3-18 下午06:41:40
 */
@Service
public class PlainHandler extends TextResponseHandler {

    public void appendDebugInfo(XLContextAttachment attach, StringBuilder content) {
        if (serverConfig.isDebugEnable()) {
            XLHttpRequest request = attach.getRequest();
            if (null != request.getParameter("debug", null)) {
                content.append("\n\n\n---DEBUG---------------------------------------------\n");
                content.append(attach.getName()).append("\n");
                content.append(attach.getTimeSpanInfo()).append("\n\n");

                List<Throwable> ts = attach.getThrowables();
                if (null != ts) {
                    for (Throwable ex : ts) {
                        StringHelper.printThrowable(content, ex).append("\n\n");
                    }
                }
            }
        }
    }

    public StringBuilder _buildContentString(XLContextAttachment attach, Object cmdReturnObj) {
        StringBuilder content = new StringBuilder();
        content.append(cmdReturnObj == null ? responseReturnNullInfo : cmdReturnObj);
        appendDebugInfo(attach, content);
        return content;
    }

    @Override
    public String buildContentString(XLContextAttachment attach, Object cmdReturnObj) {
        XLHttpResponse response = attach.getResponse();
        // ContentType type = response.getInnerContentType();
        // 当前处理器一般是放在处理链的最后一个,所以这里处理所有遗留情况
        // if (!type.equals(ContentType.plain))
        // return null;
        response.setHeaderIfEmpty(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=" + response.getContentCharset());
        StringBuilder content = _buildContentString(attach, cmdReturnObj);
        return content.toString();
    }

    @Override
    public Object handleThrowable(XLContextAttachment attach, Throwable ex) throws Exception {
        // 当前处理器一般是放在处理链的最后一个,所以这里处理所有遗留情况
        // if (!type.equals(ContentType.plain))
        // return null;
        XLHttpResponse response = attach.getResponse();
        XLHttpRequest request = attach.getRequest();
        // 2012-10-25 不要被Json弄乱 plainHandler的纯粹性，要想输出json,就用json，然后 onlyjson可以避开callback
        // if (ex instanceof RtnError) {
        // return ((RtnError) ex).getJson();
        // }
        // if (ex instanceof IllegalParameterError) {
        // return JsonObjectUtil.getRtnAndDataJsonObject(RtnConstants.PARAM_ILLEGAL, JsonObjectUtil.buildMap("msg", ex.getMessage()));
        // }
        if (ex instanceof AbstractHttpServerError) {
            HttpResponseStatus status = ((AbstractHttpServerError) ex).getStatus();
            response.setStatus(status);
            return ex.getMessage();// 内部错误只用打出其message
        }

        logThrowable(attach, request, response, ex);
        return StringHelper.printThrowableSimple(ex); // 其他错误打印简单信息
    }
}
