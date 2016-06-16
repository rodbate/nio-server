package com.xunlei.netty.httpserver.util;

import com.xunlei.netty.httpserver.component.XLHttpRequest;

/**
 * @author ZengDong
 * @since 2010-7-15 上午12:34:21
 */
public class IPGetterHelper {

    private static class DefaultIPGetter implements IPGetter {

        @Override
        public String getIP(XLHttpRequest request) {
            return request.getPrimitiveRemoteIP();
        }
    }

    public static final IPGetter DEFAULT_IPGETTER = new DefaultIPGetter();
    private static IPGetter CURRENT_IPGETTER = DEFAULT_IPGETTER;

    public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_REAL_IP = "X-Real-IP";

    /**
     * 使用当前设置好的的IPGetter获得request的ip
     */
    public static String getIP(XLHttpRequest request) {
        return CURRENT_IPGETTER.getIP(request);
    }

    /**
     * 设置当前IPGetter
     */
    public static void setIPGetter(IPGetter getter) {
        if (getter != null) {
            CURRENT_IPGETTER = getter;
        }
    }

    public static String getIP(XLHttpRequest request, String proxyHeader) {
        String proxyIp = request.getHeader(proxyHeader);
        if (proxyIp == null) {
            return request.getPrimitiveRemoteIP();
        }
        proxyIp = proxyIp.trim();
        if (proxyIp.isEmpty() || !proxyIp.contains(".")) {
            return request.getPrimitiveRemoteIP();
        }
        return proxyIp;
    }

    public static String getIPByXForwared(XLHttpRequest request) {
        String proxyIp = request.getHeader(X_FORWARDED_FOR);
        if (proxyIp == null) {
            return request.getPrimitiveRemoteIP();
        }
        proxyIp = proxyIp.trim();
        if (proxyIp.isEmpty() || !proxyIp.contains(".")) {
            return request.getPrimitiveRemoteIP();
        }

        int index = proxyIp.indexOf(',');
        if (index > 0) {
            proxyIp = proxyIp.substring(0, index);
        }
        return proxyIp;
    }

    public static String getIP(XLHttpRequest request, String... proxyHeaders) {
        for (int i = 0; i < proxyHeaders.length; i++) {
            String proxyIp = request.getHeader(proxyHeaders[i]);
            if (proxyIp == null) {
                continue;
            }
            proxyIp = proxyIp.trim();
            if (proxyIp.isEmpty() || !proxyIp.contains(".")) {
                continue;
            }
            return proxyIp;
        }
        return request.getPrimitiveRemoteIP();
    }

}
