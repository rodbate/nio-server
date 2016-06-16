package com.xunlei.netty.httpserver.component;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpMessage;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.util.CharsetTools;

/**
 * 增加 功能：setStatus,setHeaderIfEmpty 增加addCookie的方法 增加innserContentType,以适应json,及xml的封装
 * 
 * @author ZengDong
 * @since 2010-3-25 下午02:03:41
 */
public class XLHttpResponse extends DefaultHttpMessage implements HttpResponse {

    public enum ContentType {
        html, json, plain, xml
    }

    private Charset contentCharset = CharsetTools.UTF_8;// 默认是utf8,想修改的话使用setContentCharset
    private int contentLength = -1;
    private boolean contentSetted = false;
    private List<Cookie> cookies = new ArrayList<Cookie>(1);
    private long createTime = System.currentTimeMillis();
    private ContentType innerContentType = HttpServerConfig.getRespInnerContentType();
    private int keepAliveTimeout = HttpServerConfig.getKeepAliveTimeout();
    private boolean keepAliveTimeoutSetted = false;
    private HttpResponseStatus status;
    private XLContextAttachment attach;
    private String contentString;

    public XLHttpResponse(XLContextAttachment attach) {
        super(HttpVersion.HTTP_1_1);
        // 2012-4-14 zengdong 先这样改下，看下能否让android的浏览器不打开pipeline模式
        // 2012-4-19 在线上测试发现 改成 http/1.0 那些浏览器竟然还是发pipeline过来> <
        this.status = HttpResponseStatus.OK;
        this.attach = attach;
    }

    public XLHttpResponse() {// 初始化一些默认值
        super(HttpVersion.HTTP_1_1);
        this.status = HttpResponseStatus.OK;
    }

    public void addCookie(Cookie cookie) {
        this.cookies.add(cookie);
    }

    public Charset getContentCharset() {
        return contentCharset;
    }

    public String getContentString() {// 一般用于 内部查看
        if (contentString == null) {
            ChannelBuffer cb = this.getContent();
            if (cb == null) {
                return null;
            } else {
                return cb.toString(contentCharset);
            }
        }
        return contentString;
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public long getCreateTime() {
        return createTime;
    }

    public ContentType getInnerContentType() {
        return innerContentType;
    }

    public int getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return this.status;
    }

    public boolean isContentSetted() {
        return contentSetted;
    }

    /**
     * 302重定向
     * 
     * @param localtionUrl 重定向的URL
     */
    public void redirect(String localtionUrl) {
        setStatus(HttpResponseStatus.FOUND);
        setHeaderIfEmpty(HttpHeaders.Names.LOCATION, localtionUrl);
    }

    /**
     * 重定向
     * 
     * @param localtionUrl 重定向的URL
     */
    public void redirect(String localtionUrl, HttpResponseStatus status) {
        setStatus(status);
        setHeaderIfEmpty(HttpHeaders.Names.LOCATION, localtionUrl);
    }

    @Override
    public void setContent(ChannelBuffer content) {
        super.setContent(content);
        if (content != null) {
            contentLength = content.readableBytes();
        }
        contentSetted = true;
    }

    public void setContentCharset(Charset contentCharset) {
        this.contentCharset = contentCharset;
    }

    public void setContentString(String contentStr) {
        this.contentString = contentStr;// 如果开启 deflate功能时，HttpContentCompressor 会在编码时setContent(channelBuffer) 为了让accesslog正常显示，这里提前存下 contentStr
        setContent(ChannelBuffers.copiedBuffer(contentStr, contentCharset));
    }

    /**
     * 注意不要在这里设置keepAlive相关参数,要设置请使用setKeepAliveTimeout
     */
    public boolean setHeaderIfEmpty(String name, String value) {
        if (getHeader(name) == null) {
            setHeader(name, value);
            return true;
        }
        return false;
    }

    public void setInnerContentType(ContentType innerContentType) {
        this.innerContentType = innerContentType;
    }

    public void setKeepAliveTimeout(int keepAliveTimeout) {
        if (keepAliveTimeout < 0) {
            throw new IllegalArgumentException("keepAliveTimeout:" + keepAliveTimeout + " cant be nagative");
        }
        this.keepAliveTimeoutSetted = true;
        this.keepAliveTimeout = keepAliveTimeout;
    }

    @Override
    public void setStatus(HttpResponseStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getProtocolVersion().getText() + ' ' + getStatus().toString();
    }

    @Override
    public void setChunked(boolean chunked) {
        super.setChunked(chunked);
        if (chunked) {// 测试发现如果不带此header,浏览器会一直 等待(转圈圈)
            this.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        } else {
            this.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        }
    }

    /**
     * <pre>
     * 把所有配置好的 cookies变成 实际要发送响应时用到的set-cookie: 响应头
     * 
     * 此方法仅供内部使用
     */
    public void packagingCookies() {
        List<Cookie> cookies = getCookies();
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                cookieEncoder.addCookie(cookie);
                addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
            }
        }
    }

    public boolean isKeepAliveTimeoutSetted() {
        return keepAliveTimeoutSetted;
    }

    public XLContextAttachment getAttach() {
        return attach;
    }
}
