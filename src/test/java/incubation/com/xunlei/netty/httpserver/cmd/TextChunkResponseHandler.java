package incubation.com.xunlei.netty.httpserver.cmd;

import java.util.List;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.util.HttpServerConfig;

/**
 * @author ZengDong
 * @since 2011-3-18 下午01:53:38
 */
@Service
public class TextChunkResponseHandler {

    @Autowired
    private HttpServerConfig serverConfig;

    private void setCookie(XLHttpResponse response) {// TODO:是否移动到response
        // 配合XLHttpResponse设置cookie
        List<Cookie> cookies = response.getCookies();
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                cookieEncoder.addCookie(cookie);
                response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
            }
        }
    }

    public void writeChunkBegin(XLContextAttachment attach) {
        XLHttpResponse response = attach.getResponse();
        response.setChunked(true);
        Channel channel = attach.getChannelHandlerContext().getChannel();
        attach.checkChannelOrThread();

        serverConfig.getStatistics().writeBegin(attach);// TODO:这里是否开始计算编码开始？
        setCookie(response);

        channel.write(response);
    }

    public ChannelFuture writeChunk(XLContextAttachment attach, HttpChunk chunk) {
        Channel channel = attach.getChannelHandlerContext().getChannel();
        attach.checkChannelOrThread();
        return channel.write(chunk);

        // TODO:是否在attch或者在 respnose中保存这些chunk?
    }

    public void writeChunkEnd(XLContextAttachment attach) {
        ChannelFuture future = writeChunk(attach, HttpChunk.LAST_CHUNK);
        Channel channel = attach.getChannelHandlerContext().getChannel();
        boolean close = true;// TODO:这里判断是否关闭channel的依据是?
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
