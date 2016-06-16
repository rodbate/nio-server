package com.xunlei.netty.httpserver.component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.codec.http.multipart.Attribute;
import org.jboss.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.DiskAttribute;
import org.jboss.netty.handler.codec.http.multipart.DiskFileUpload;
import org.jboss.netty.handler.codec.http.multipart.HttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.exception.IllegalParameterError;
import com.xunlei.netty.httpserver.util.IPGetterHelper;
import com.xunlei.util.CharsetTools;
import com.xunlei.util.CollectionUtil;
import com.xunlei.util.EmptyChecker;
import com.xunlei.util.HttpUtil;
import com.xunlei.util.Log;
import com.xunlei.util.MapUtil;
import com.xunlei.util.StringTools;
import com.xunlei.util.ValueUtil;

/**
 * 增加对请求参数的解码,增加对cookie的解码,增加获得remoteip的方法,增加request生成时间,用于计算处理时间
 * 
 * @author ZengDong
 * @since 2010-3-25 下午02:27:32
 */
public class XLHttpRequest extends DefaultHttpRequest {

    private static final String COOKIE = "COOKIE";
    // private static final CookieDecoder cookieDecoder = new CookieDecoder();
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed MINSIZE
    private static final Logger log = Log.getLogger();
    private static final String PARAMETER = "PARAMETER";
    static {
        // TODO:程序正常退出时,不删除文件(因为发现里面的list只增不减)
        DiskFileUpload.deleteOnExitTemporaryFile = false; // should delete file on exit (in normal exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = false; // should delete file on exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }

    public void clean() {
        if (httpPostRequestDecoder != null) {
            httpPostRequestDecoder.cleanFiles();
            httpPostRequestDecoder = null;// 短连接情况下，避免都cleanFiles两次
        }
    }

    private Map<String, List<String>> initParametersByPost(Map<String, List<String>> params, HttpPostRequestDecoder httpPostRequestDecoder) {
        if (httpPostRequestDecoder == null) {
            return params;
        }

        try {
            List<InterfaceHttpData> datas = httpPostRequestDecoder.getBodyHttpDatas();
            if (datas != null) {
                for (InterfaceHttpData data : datas) {
                    if (data instanceof Attribute) {
                        Attribute attribute = (Attribute) data;
                        try {
                            String key = attribute.getName();
                            String value = attribute.getValue();

                            List<String> ori = params.get(key);
                            if (ori == null) {
                                ori = new ArrayList<String>(1);
                                params.put(key, ori);
                            }
                            ori.add(value);
                        } catch (IOException e) {
                            log.error("cant init attribute,req:{},attribute:{}", new Object[] {
                                this,
                                attribute,
                                e
                            });
                        }
                    }
                }
            }
        } catch (NotEnoughDataDecoderException e) {
            log.error("req:{}", this, e);
        }
        return params;
    }

    private Charset charset4ContentDecoder = CharsetTools.UTF_8;
    private Charset charset4QueryStringDecoder = CharsetTools.UTF_8;
    private Map<String, Cookie> cookies;
    private long createTime = System.currentTimeMillis();// 2011-12-17 注意，这是解码开始的信号，而不是解码结束的信息
    private HttpPostRequestDecoder httpPostRequestDecoder;

    private boolean httpPostRequestDecoderInit;
    private SocketAddress localAddress;
    private String localIP;
    private Map<String, List<String>> parameters;
    private Map<String, List<String>> parametersByPost;
    @Deprecated
    private PostContentDecoder postContentDecoder;
    private String primitiveRemoteIP;
    private QueryStringDecoder queryStringDecoder;
    private SocketAddress remoteAddress;
    private String remoteIP;

    public XLHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion, method, uri);
    }

    private boolean getBoolean(String key, String v, String type) {
        if (v == null) {
            throw new IllegalParameterError(key, this, type);
        }
        if (v.equals("true") || v.equalsIgnoreCase("y") || v.equals("1")) {
            return true;
        }
        return false;
    }

    public Charset getCharset4QueryStringDecoder() {
        return charset4QueryStringDecoder;
    }

    public Cookie getCookie(String name) {
        return getCookies().get(name);
    }

    public Map<String, Cookie> getCookies() {
        if (cookies == null) {
            cookies = new HashMap<String, Cookie>();
            List<String> cookieList = getHeaders(HttpHeaders.Names.COOKIE);// 把所有 header:cookie拿出来
            for (String cookieString : cookieList) {
                if (StringTools.isEmpty(cookieString)) {
                    continue;
                }

                // 为了最大限度解析cookie,这里重新使用简单的cookieDecoder,内部只处理key-value,不处理复杂情况
                XLCookieDecoder.decode(cookieString, cookies);

                /**
                 * <pre>
                 *                 try {
                 *                     Set<Cookie> set = cookieDecoder.decode(cookieString);
                 *                     for (Cookie c : set) {
                 *                         cookies.put(c.getName(), c);// TODO:有可能会覆盖
                 *                     }
                 *                 } catch (Throwable e) {// 不要让decode错漫延
                 *                     // cookieDecoder.decode 方法可能会报以下错：
                 *                     
                 *                     //如："usernick='__%E6%93%8E%22%E5%A4%A9%E6%9F%B1;  __utmz=166345655.1293106555.2.2.utmcsr=search.xunlei.com|utmccn=(referral)|utmcmd=referral|utmcct=/search.php; im_peer_cookie=null; KANKANWEBUID=4edf51e9f1f92323dc4e27cb065ddecc; _xltj=35a1293106537926b2c; luserid=92504464; lsessionid=5853D4000A0A2B6CB4860F46B20DA33988F018087092B214D52265C81EFF9F3F9BC0A5078753D1DB9FD91B096722DAA1944AE4871A3EFB63A5D60E21DC69E9A66C06B46EC6948D1347D209A5C16EC145; uservip=2; _s35=1293717183140b1293106537926b2bhttp%3A//search.xunlei.com/search.php%3Fkeyword%3D%25E7%259B%2597%25E6%25A2%25A6%25E7%25A9%25BA%25E9%2597%25B4; __utmb=166345655.1.10.1293106555; __xltjbr=1293106343015; fref=act_002_002; seccode=101223201241322ed16e682484ae3049d5d4accabe3f; vipLevel=2; vipExpire=-1; lastBrowse=13475%7C12572; _xltj=35a1293106537926b2c61a1293106628379b1c"
                 *                     // 1.java.lang.StackOverflowError: null
                 *                     // at java.util.regex.Pattern$Branch.match(Pattern.java:4114) ~[na:1.6.0_18]
                 *                     // at java.util.regex.Pattern$GroupHead.match(Pattern.java:4168) ~[na:1.6.0_18]
                 *                     // at java.util.regex.Pattern$Loop.match(Pattern.java:4295) ~[na:1.6.0_18]
                 *                     // at java.util.regex.Pattern$GroupTail.match(Pattern.java:4227) ~[na:1.6.0_18]
                 *                     // at java.util.regex.Pattern$BranchConn.match(Pattern.java:4078) ~[na:1.6.0_18]
                 *                     // at java.util.regex.Pattern$CharProperty.match(Pattern.java:3345) ~[na:1.6.0_18]
                 * 
                 *                     // 2.java.lang.IllegalArgumentException: name contains non-ascii character:xxx
                 *                     // at org.jboss.netty.handler.codec.http.DefaultCookie.<init>(DefaultCookie.java:79) ~[netty-3.2.3.Final.jar:na]
                 *                     log.error("cannot decode cookieString:{},throwable:{}-{}", new Object[] { cookieString, e.getClass().getName(), e.getMessage() });
                 * }
                 **/
            }
        }
        return cookies;
    }

    public String getCookieValue(String cookieName) {
        if (StringTools.isEmpty(cookieName)) {
            throw new IllegalArgumentException("cookieName isEmpty:[" + cookieName + "]");
        }
        Cookie cookie = getCookie(cookieName);
        return cookie == null ? null : cookie.getValue();
    }

    public String getCookieValue(String cookieName, String defaultValue) {
        if (StringTools.isEmpty(cookieName)) {
            throw new IllegalArgumentException("cookieName isEmpty:[" + cookieName + "]");
        }
        Cookie cookie = getCookie(cookieName);
        if (cookie == null) {
            return defaultValue;
        }
        return cookie.getValue();
    }

    public boolean getCookieValueBoolean(String cookieName) {
        return getBoolean(cookieName, getCookieValue(cookieName), COOKIE);
    }

    public boolean getCookieValueBoolean(String cookieName, boolean defaultValue) {
        return ValueUtil.getBoolean(getCookieValue(cookieName), defaultValue);
    }

    public String getCookieValueCompelled(String cookieName) {
        String v = getCookieValue(cookieName);
        if (v == null) {
            throw new IllegalParameterError(cookieName, this, COOKIE);
        }
        return v;
    }

    public double getCookieValueDouble(String cookieName) {
        return getDouble(cookieName, getCookieValue(cookieName), COOKIE);
    }

    public double getCookieValueDouble(String cookieName, int defaultValue) {
        return ValueUtil.getDouble(getCookieValue(cookieName), defaultValue);
    }

    public float getCookieValueFloat(String cookieName) {
        return getFloat(cookieName, getCookieValue(cookieName), COOKIE);
    }

    public float getCookieValueFloat(String cookieName, int defaultValue) {
        return ValueUtil.getFloat(getCookieValue(cookieName), defaultValue);
    }

    public long getCookieValueInteger(String cookieName) {
        return getInteger(cookieName, getCookieValue(cookieName), COOKIE);
    }

    public int getCookieValueInteger(String cookieName, int defaultValue) {
        return ValueUtil.getInteger(getCookieValue(cookieName), defaultValue);
    }

    public long getCookieValueLong(String cookieName) {
        return getLong(cookieName, getCookieValue(cookieName), COOKIE);
    }

    public long getCookieValueLong(String cookieName, long defaultValue) {
        return ValueUtil.getLong(getCookieValue(cookieName), defaultValue);
    }

    public long getCreateTime() {
        return createTime;
    }

    private double getDouble(String key, String v, String type) {
        if (v == null) {
            throw new IllegalParameterError(key, this, type);
        }
        try {
            return Double.valueOf(v);
        } catch (Exception e) {
            throw new IllegalParameterError(key, this, type, " must be Double");
        }
    }

    private float getFloat(String key, String v, String type) {
        if (v == null) {
            throw new IllegalParameterError(key, this, type);
        }
        try {
            return Float.valueOf(v);
        } catch (Exception e) {
            throw new IllegalParameterError(key, this, type, " must be Float");
        }
    }

    public HttpPostRequestDecoder getHttpPostRequestDecoder() {
        if (!httpPostRequestDecoderInit) {
            HttpMethod method = getMethod();
            if (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT)) {
                try {
                    httpPostRequestDecoder = new HttpPostRequestDecoder(factory, this, charset4ContentDecoder);
                } catch (ErrorDataDecoderException e) {// 这里如果解析失败,比较严重,要特别关注
                    log.error("request postDataDecode error:{}", this, e);
                } catch (IncompatibleDataDecoderException e) {
                }
            }
            httpPostRequestDecoderInit = true;
        }
        return httpPostRequestDecoder;
    }

    private int getInteger(String key, String v, String type) {
        if (v == null) {
            throw new IllegalParameterError(key, this, type);
        }
        try {
            return Integer.valueOf(v);
        } catch (Exception e) {
            throw new IllegalParameterError(key, this, type, " must be Integer");
        }
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public String getLocalIP() {
        if (localIP == null) {
            try {
                localIP = HttpUtil.getIP((InetSocketAddress) getLocalAddress());
            } catch (Exception e) {
                log.error("", e);
                localIP = "";
            }
        }
        return localIP;
    }

    public int getLocalPort() {
        return ((InetSocketAddress) localAddress).getPort();
    }

    private long getLong(String key, String v, String type) {
        if (v == null) {
            throw new IllegalParameterError(key, this, type);
        }
        try {
            return Long.valueOf(v);
        } catch (Exception e) {
            throw new IllegalParameterError(key, this, type, " must be Long");
        }
    }

    public String getParameter(String key) {
        if (StringTools.isEmpty(key)) {
            throw new IllegalArgumentException("key isEmpty:[" + key + "]");
        }
        List<String> v = getParameters().get(key);
        if (v != null) {
            return v.get(0);
        }
        return getParameterByPost(key);
    }

    public String getParameter(String key, String defaultValue) {
        String v = getParameter(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public boolean getParameterBoolean(String key) {
        return getBoolean(key, getParameter(key), PARAMETER);
    }

    public boolean getParameterBoolean(String key, boolean defaultValue) {
        return ValueUtil.getBoolean(getParameter(key), defaultValue);
    }

    public String getParameterByPost(String key) {
        // 为了能够跟getParameter统一调用方式,不用下列方法来实时获得
        // HttpPostRequestDecoder postDecoder = getHttpPostRequestDecoder();
        // try {
        // List<InterfaceHttpData> datas = postDecoder.getBodyHttpDatas(key);
        // if (datas != null) {
        // for (InterfaceHttpData data : datas) {
        // if (data instanceof Attribute) {
        // return ((Attribute) data).getValue();
        // }
        // }
        // }
        // } catch (Exception e) {
        // log.error("", e);
        // }

        List<String> v = getParametersByPost().get(key);
        if (v != null) {
            return v.get(0);
        }
        return null;
    }

    public String getParameterCompelled(String key) {
        String v = getParameter(key);
        if (v == null) {
            throw new IllegalParameterError(key, this, PARAMETER);
        }
        return v;
    }

    public double getParameterDouble(String key) {
        return getDouble(key, getParameter(key), PARAMETER);
    }

    public double getParameterDouble(String key, double defaultValue) {
        return ValueUtil.getDouble(getParameter(key), defaultValue);
    }

    public float getParameterFloat(String key) {
        return getFloat(key, getParameter(key), PARAMETER);
    }

    public float getParameterFloat(String key, float defaultValue) {
        return ValueUtil.getFloat(getParameter(key), defaultValue);
    }

    public int getParameterInteger(String key) {
        return getInteger(key, getParameter(key), PARAMETER);
    }

    public int getParameterInteger(String key, int defaultValue) {
        return ValueUtil.getInteger(getParameter(key), defaultValue);
    }

    public long getParameterLong(String key) {
        return getLong(key, getParameter(key), PARAMETER);
    }

    public long getParameterLong(String key, long defaultValue) {
        return ValueUtil.getLong(getParameter(key), defaultValue);
    }

    /**
     * 注意这里只是把uri中的请求参数提取出来,post中的请求参数需要使用getParametersByPost
     */
    public Map<String, List<String>> getParameters() {// TODO:这里是不是应该改成getParametersByGet?
        if (parameters == null) {
            try {
                Map<String, List<String>> params = getQueryStringDecoder().getParameters();
                // 不把get跟post的混在同一个map上
                // initParametersByPost(params, getHttpPostRequestDecoder());
                parameters = params;
            } catch (Exception e) {
                log.error("queryString decode fail,req:{},{}:{}", new Object[] {
                    this,
                    e.getClass(),
                    e.getMessage()
                });
                parameters = Collections.emptyMap();
            }
            // 原来的方式中如果post请求中有带其他数据(如二进制数据)时,会出现报错情况
            // 如 java.lang.IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern - For input string: "S这里有乱码"

            // if (HttpMethod.POST.equals(getMethod())) {
            // parameters = getPostContentDecoder().getParameters();// 注意：如果是Post过来的请求,就找Get里面的QueryString
            // } else {
            // parameters = getQueryStringDecoder().getParameters();
            // }
        }
        return parameters;
    }

    /**
     * 动态添加参数，用以适应特殊情况
     * 
     * @param kv 参数key和value，提交的参数个数必须是偶数个
     */
    public void addParameters(Object... keyvalue) {
        MapUtil.checkKeyValueLength(keyvalue);
        Map<String, List<String>> params = getParameters();
        if (Collections.EMPTY_MAP == params) { // emptyMap 不可修改，要改掉
            params = parameters = new HashMap<String, List<String>>();
        }
        for (int i = 0; i < keyvalue.length; i++) {
            String k = keyvalue[i++].toString();
            List<String> v = params.get(k);
            if (v == null) {
                Object newV = keyvalue[i];
                List newVList = newV instanceof List ? (List) newV : CollectionUtil.buildList(newV.toString());
                params.put(k, newVList);
            } else {
                v.add(keyvalue[i].toString());
            }
        }
    }

    public Map<String, List<String>> getParametersByPost() {
        if (parametersByPost == null) {
            HttpPostRequestDecoder httpPostRequestDecoder = getHttpPostRequestDecoder();
            if (httpPostRequestDecoder != null) {
                parametersByPost = initParametersByPost(new HashMap<String, List<String>>(0), httpPostRequestDecoder);
            } else {
                parametersByPost = Collections.emptyMap();
            }
        }
        return parametersByPost;
    }

    public String[] getParameterValues(String key) {
        List<String> result = getParameters().get(key);
        if (EmptyChecker.isNotEmpty(result)) {
            return (String[]) result.toArray();
        }

        result = getParametersByPost().get(key);
        if (EmptyChecker.isNotEmpty(result)) {
            return (String[]) result.toArray();
        }
        return null;
    }

    public String getPath() {
        return getQueryStringDecoder().getPath();
    }

    @Deprecated
    public PostContentDecoder getPostContentDecoder() {
        if (postContentDecoder == null) {
            ChannelBuffer buffer = getContent();
            if (buffer != null) {
                postContentDecoder = new PostContentDecoder(buffer.toString(charset4ContentDecoder));
            }
        }
        return postContentDecoder;
    }

    public String getPrimitiveRemoteIP() {
        if (primitiveRemoteIP == null) {
            try {
                primitiveRemoteIP = HttpUtil.getIP((InetSocketAddress) remoteAddress);
            } catch (Exception e) {
                log.error("", e);
                primitiveRemoteIP = "";
            }
        }
        return primitiveRemoteIP;
    }

    public QueryStringDecoder getQueryStringDecoder() {
        if (queryStringDecoder == null) {
            queryStringDecoder = new QueryStringDecoder(getUri(), charset4QueryStringDecoder);
        }
        return queryStringDecoder;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public String getRemoteHost() {
        return ((InetSocketAddress) remoteAddress).getHostName();
    }

    public String getRemoteIP() {
        if (remoteIP == null) {
            remoteIP = IPGetterHelper.getIP(this);
        }
        return remoteIP;
    }

    public int getRemotePort() {
        return ((InetSocketAddress) remoteAddress).getPort();
    }

    public void offerChunk(HttpChunk chunk) throws Exception {
        getHttpPostRequestDecoder().offer(chunk);
    }

    public void setCharset4QueryStringDecoder(Charset charset4QueryStringDecoder) {
        this.charset4QueryStringDecoder = charset4QueryStringDecoder;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String toString() {
        return Integer.toHexString(hashCode()) + this.getRemoteAddress() + "/" + this.getMethod() + " " + this.getUri();
    }

    private String url;

    public String getUrl() {
        if (url == null) {
            String host = getHeader(HttpHeaders.Names.HOST);
            String port = getLocalPort() == 80 ? "" : ":" + getLocalPort();
            url = "http://" + (StringTools.isEmpty(host) ? getLocalIP() + port : host) + getUri();
        }
        return url;
    }

    public StringBuilder getSimpleInfo() {
        Map<String, List<String>> params = getParameters();
        Map<String, List<String>> post_params = getParametersByPost();
        int keyMaxLen = 20;
        for (String key : params.keySet()) {
            keyMaxLen = Math.max(keyMaxLen, key.length());
        }
        for (String key : post_params.keySet()) {
            keyMaxLen = Math.max(keyMaxLen, key.length());
        }
        String fmt = "%" + keyMaxLen + "s  %s\n";

        StringBuilder r = new StringBuilder();
        r.append(String.format(fmt, Integer.toHexString(hashCode()), getRemoteAddress()));
        r.append(String.format(fmt, getMethod(), getUrl()));

        String content = getContentString();
        if (StringTools.isNotEmpty(content)) {
            r.append("CONTENT:\n" + content + "\n");
        }

        if (!params.isEmpty()) {
            r.append("PARAM:\n");
            for (Entry<String, List<String>> p : params.entrySet()) {
                String key = p.getKey();
                List<String> vals = p.getValue();
                for (String val : vals) {
                    r.append(String.format(fmt, key, val));
                }
            }
        }
        if (!post_params.isEmpty()) {
            r.append("POST_PARAM:\n");
            for (Entry<String, List<String>> p : post_params.entrySet()) {
                String key = p.getKey();
                List<String> vals = p.getValue();
                for (String val : vals) {
                    r.append(String.format(fmt, key, val));
                }
            }
        }

        Collection<Cookie> cookies = getCookies().values();
        if (EmptyChecker.isNotEmpty(cookies)) {
            r.append("COOKIES  :\n");
            for (Cookie c : cookies) {
                r.append(String.format(fmt, c.getName(), c.getValue()));
            }
        }
        return r;
    }

    public StringBuilder getDetailInfo() {
        Map<String, List<String>> params = getParameters();
        Map<String, List<String>> post_params = getParametersByPost();
        int keyMaxLen = 20;
        for (String key : params.keySet()) {
            keyMaxLen = Math.max(keyMaxLen, key.length());
        }
        for (String key : post_params.keySet()) {
            keyMaxLen = Math.max(keyMaxLen, key.length());
        }
        String fmt = "%" + keyMaxLen + "s  %s\n";

        StringBuilder r = new StringBuilder("REQUEST:\n");
        r.append(String.format(fmt, getMethod(), getUrl()));
        r.append(String.format(fmt, getProtocolVersion().getText(), getRemoteAddress() + "->" + getLocalAddress()));
        // StringHelper.append(r, getMethod(), " ", getUrl(), "\n", getProtocolVersion().getText(), " ", getRemoteAddress(), "->", getLocalAddress(), "\n");
        if (!getHeaderNames().isEmpty()) {
            r.append("HEADER:\n");
            for (String name : getHeaderNames()) {
                for (String value : getHeaders(name)) {
                    r.append(String.format(fmt, name, value));
                }
            }
        }
        String content = getContentString();
        if (StringTools.isNotEmpty(content)) {
            r.append("CONTENT:\n" + content + "\n");
        }

        if (!params.isEmpty()) {
            r.append("PARAM:\n");
            for (Entry<String, List<String>> p : params.entrySet()) {
                String key = p.getKey();
                List<String> vals = p.getValue();
                for (String val : vals) {
                    r.append(String.format(fmt, key, val));
                }
            }
        }
        if (!post_params.isEmpty()) {
            r.append("POST_PARAM:\n");
            for (Entry<String, List<String>> p : post_params.entrySet()) {
                String key = p.getKey();
                List<String> vals = p.getValue();
                for (String val : vals) {
                    r.append(String.format(fmt, key, val));
                }
            }
        }
        return r;
    }

    public String getContentString(Charset charset) {
        ChannelBuffer content = getContent();
        return new String(content.array(), charset);
    }

    public String getContentString() {
        ChannelBuffer content = getContent();
        if (content.hasArray()) {
            return new String(content.array(), charset4ContentDecoder);
        }
        return "";
    }

    /**
     * 取post内容，直接使用ByteOrder.BIG_ENDIAN返回
     * 
     * @return
     */
    public ChannelBuffer getPostContent() {
        return getPostContent(ByteOrder.BIG_ENDIAN);
    }

    /**
     * 取post内容
     * 
     * <pre>
     * byte[] bufferBytes = request.getContent().array();
     * 
     * This does not pay attention of the readerIndex and writerIndex. It worked before as we just create a new array and copy the bytes over in <= 3.5.1.Final, but you should never depend on that. It would even not work if you would use direct buffers, so thats the other downside.
     * In newer versions of netty we share the backed array and so we safe memory copies and so are faster. So the correct way of get all readable bytes is:
     * 
     * byte[] bufferBytes = new byte[request.getContent().readableBytes()];
     * request.getContent().getBytes(request.getContent().readerIndex(), bufferBytes);
     * </pre>
     * 
     * @return
     */
    public ChannelBuffer getPostContent(ByteOrder byteOrder) {
        ChannelBuffer content = getContent();
        if (content.readable()) {
            byte[] contentArray = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), contentArray);
            return ChannelBuffers.copiedBuffer(byteOrder, contentArray);
        }
        return ChannelBuffers.EMPTY_BUFFER;
    }

    public Charset getCharset4ContentDecoder() {
        return charset4ContentDecoder;
    }

    public void setCharset4ContentDecoder(Charset charset4ContentDecoder) {
        this.charset4ContentDecoder = charset4ContentDecoder;
    }

    public String getHeader(String name, String defaultValue) {
        String value = getHeader(name);
        if (null == value) {
            return defaultValue;
        }
        return value;
    }
}
