package com.xunlei.netty.httpserver.component;

import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.util.internal.CaseIgnoringComparator;
import org.slf4j.Logger;
import com.xunlei.util.Log;

/**
 * 简单解析Header: Cookie
 * 
 * @author ZengDong
 * @since 2010-12-24 下午05:02:43
 */
public class XLCookieDecoder {

    public static final XLCookieDecoder INSTANCE = new XLCookieDecoder();
    private static final Logger log = Log.getLogger();
    public static final Set<String> RESERVED_NAMES = new TreeSet<String>(CaseIgnoringComparator.INSTANCE);
    static {
        RESERVED_NAMES.add("$Domain");
        RESERVED_NAMES.add("$Path");
        RESERVED_NAMES.add("$Comment");
        RESERVED_NAMES.add("$CommentURL");
        RESERVED_NAMES.add("$Discard");
        RESERVED_NAMES.add("$Port");
        RESERVED_NAMES.add("$Max-Age");
        RESERVED_NAMES.add("$Expires");
        RESERVED_NAMES.add("$Version");
        RESERVED_NAMES.add("$Secure");
        RESERVED_NAMES.add("$HTTPOnly");
    }

    /**
     * <pre>
     *     public static void main(String[] args) {
     *         String cookieString = "usernick='__%E6%93%8E%22%E5%A4%A9%E6%9F%B1;  __utmz=166345655.1293106555.2.2.utmcsr=search.xunlei.com|utmccn=(referral)|utmcmd=referral|utmcct=/search.php; im_peer_cookie=null; KANKANWEBUID=4edf51e9f1f92323dc4e27cb065ddecc; _xltj=35a1293106537926b2c; luserid=92504464; lsessionid=5853D4000A0A2B6CB4860F46B20DA33988F018087092B214D52265C81EFF9F3F9BC0A5078753D1DB9FD91B096722DAA1944AE4871A3EFB63A5D60E21DC69E9A66C06B46EC6948D1347D209A5C16EC145; uservip=2; _s35=1293717183140b1293106537926b2bhttp%3A//search.xunlei.com/search.php%3Fkeyword%3D%25E7%259B%2597%25E6%25A2%25A6%25E7%25A9%25BA%25E9%2597%25B4; __utmb=166345655.1.10.1293106555; __xltjbr=1293106343015; fref=act_002_002; seccode=101223201241322ed16e682484ae3049d5d4accabe3f; vipLevel=2; vipExpire=-1; lastBrowse=13475%7C12572; _xltj=35a1293106537926b2c61a1293106628379b1c";
     *         mainTest(cookieString);
     * 
     *         mainTest("foo=bar; a=b");
     *         mainTest("foo=bar;a=b");
     *         mainTest("foo=bar;a=b;");
     *         mainTest("foo=bar;a=b; ");
     *         mainTest("foo=bar;a=b; ;");
     *         mainTest("foo=;a=b; ;");
     *         mainTest("foo;a=b; ;");
     *         // v1
     *         mainTest("$Version=1; foo=bar;a=b");
     *         mainTest("$Version=\"1\"; foo='bar'; $Path=/path; $Domain=\"localhost\"");
     *         mainTest("$Version=1;foo=bar;a=b; ; ");
     *         mainTest("$Version=1;foo=;a=b; ; ");
     *         mainTest("$Version=1;foo= ;a=b; ; ");
     *         mainTest("$Version=1;foo;a=b; ; ");
     *         mainTest("$Version=1;foo=\"bar\";a=b; ; ");
     *         mainTest("$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ");
     *         mainTest("$Version=1;foo=\"bar\";$Domain=apache.org;a=b");
     *         mainTest("$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com");
     *         // rfc2965
     *         mainTest("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b");
     *         // wrong
     *         mainTest("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b");
     *     }
     * 
     *     public static void mainTest(String cookieString) {
     *         System.out.println(INSTANCE.decode(cookieString, new HashMap<String, Cookie>()).values());
     * }
     */

    public static String stripQuote(String value) {
        if ((value.startsWith("\"")) && (value.endsWith("\""))) {
            try {
                return value.substring(1, value.length() - 1);
            } catch (Exception ex) {
            }
        }
        return value;
    }

    /**
     * <pre>
     * http://kickjava.com/src/org/apache/tomcat/util/http/Cookies.java.htm
     * http://www.docjar.com/html/api/org/apache/tomcat/util/http/Cookies.java.html
     * 
     * 其他可见：
     * http://www.docjar.com/html/api/com/sonalb/net/http/cookie/RFC2965CookieParser.java.html
     */
    public static Map<String, Cookie> decode(String cookieString, Map<String, Cookie> map) {
        // normal cookie, with a string value.
        // This is the original code, un-optimized - it shouldn't
        // happen in normal case
        StringTokenizer tok = new StringTokenizer(cookieString, ";", false);
        // if (!tok.hasMoreTokens()) {
        // return Collections.emptyMap();
        // }
        // Map<String, Cookie> r = new HashMap<String, Cookie>();
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            int i = token.indexOf("=");
            if (i > -1) {
                // XXX
                // the trims here are a *hack* -- this should
                // be more properly fixed to be spec compliant
                String name = token.substring(0, i).trim();
                if (RESERVED_NAMES.contains(name)) {
                    continue;
                }

                String value = stripQuote(token.substring(i + 1).trim()); // RFC 2109 and bug
                try {
                    map.put(name, new DefaultCookie(name, value));
                } catch (Exception e) {
                    log.warn("new DefaultCookie fail,name:{},value:{}", new Object[] {
                        name,
                        value
                    });// 这里不打印堆栈
                }
            } else {
                // we have a bad cookie.... just let it go
            }
        }
        return map;
    }
}
