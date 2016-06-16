/** filename:PostContentDecoder.java */
package com.xunlei.netty.httpserver.component;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.util.CharsetUtil;

/**
 * 注意：以后可能官方实现 http://www.jboss.org/netty/community.html#nabble-td4528275 http://fisheye.jboss.org/browse/Netty/branches/httpPost/src/main/java/org/jboss/netty/handler/codec/http
 * 
 * @author huangtinghai
 * @since 2010-5-25
 */
@Deprecated
public class PostContentDecoder {

    private static final Pattern PARAM_PATTERN = Pattern.compile("([^=]*)=([^&]*)&*");
    static Charset charset = CharsetUtil.UTF_8;;
    private final Map<String, List<String>> params = new HashMap<String, List<String>>();
    private String postContent;

    /**
     * Creates a new decoder that decodes the specified postContent encoded in the specified charset.
     */
    public PostContentDecoder(String postContent) {
        this(postContent, charset);
    }

    /**
     * Creates a new decoder that decodes the specified postContent encoded in the specified charset.
     */
    public PostContentDecoder(String postContent, Charset charset) {
        if (postContent == null) {
            throw new NullPointerException("postContent");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.postContent = postContent;
        PostContentDecoder.charset = charset;
    }

    public Map<String, List<String>> getParameters() {
        decode();
        return params;
    }

    private void decode() {
        decodeParams(postContent);
    }

    private void decodeParams(String s) {
        Matcher m = PARAM_PATTERN.matcher(s);
        int pos = 0;
        while (m.find(pos)) {
            pos = m.end();
            String key = decodeComponent(m.group(1), charset);
            String value = decodeComponent(m.group(2), charset);
            List<String> values = params.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                params.put(key, values);
            }
            values.add(value);
        }
    }

    private static String decodeComponent(String s, Charset charset) {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charset.name());
        }
    }

    public String getPostContent() {
        return postContent;
    }
}
