package com.xunlei.netty.httpserver.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import com.xunlei.util.Log;

/**
 * 请使用com.xunlei.util.HttpUtil
 * 
 * @author ZengDong
 * @since 2010-5-6 上午09:42:56
 */
@Deprecated
public class HttpUtil {

    private static final Logger log = Log.getLogger();
    private static Set<String> localIPSet;
    private static String localSampleIP;

    /**
     * 获得inetSocketAddress对应的IP地址
     * 
     * @param session
     * @return
     */
    public static String getIP(InetSocketAddress inetSocketAddress) {
        if (inetSocketAddress != null) {
            InetAddress addr = inetSocketAddress.getAddress();
            if (addr != null) {
                return addr.getHostAddress();
            }
        }
        return "";
    }

    public static Set<String> getLocalIP() {
        if (localIPSet == null) {
            localIPSet = new LinkedHashSet<String>(3);
            try {
                Enumeration<?> e1 = NetworkInterface.getNetworkInterfaces();
                while (e1.hasMoreElements()) {
                    NetworkInterface ni = (NetworkInterface) e1.nextElement();

                    Enumeration<?> e2 = ni.getInetAddresses();
                    while (e2.hasMoreElements()) {
                        InetAddress ia = (InetAddress) e2.nextElement();
                        if (ia instanceof Inet6Address) {
                            continue;
                        }
                        localIPSet.add(ia.getHostAddress());
                    }
                }
            } catch (SocketException e) {
                log.error("", e);
            }
        }
        return localIPSet;
    }

    public static String getLocalSampleIP() {
        if (localSampleIP == null) {
            Set<String> set = getLocalIP();
            for (String str : set) { // 取第一个不是127.0.0.1的IP
                if (!str.equals("127.0.0.1")) {
                    localSampleIP = str;
                    break;
                }
            }
        }
        return localSampleIP;
    }
}
