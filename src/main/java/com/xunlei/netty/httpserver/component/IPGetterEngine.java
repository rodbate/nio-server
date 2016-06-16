package com.xunlei.netty.httpserver.component;

import java.util.Arrays;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.util.IPGetter;
import com.xunlei.netty.httpserver.util.IPGetterHelper;
import com.xunlei.spring.AfterConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;

/**
 * @author ZengDong
 * @since 2011-3-11 下午03:32:09
 */
@Service
public class IPGetterEngine {

    @Config("forwardProxyHeaderNames")
    private String[] forwardProxyHeaderNames;
    private static final Logger log = Log.getLogger();

    @AfterConfig
    public void init() {
        if (forwardProxyHeaderNames == null) {
            log.debug("http server IpGetter:Default");
        } else {
            log.warn("http server IpGetter:Config:{}", Arrays.toString(forwardProxyHeaderNames));
            IPGetterHelper.setIPGetter(new IPGetter() {

                @Override
                public String getIP(XLHttpRequest request) {
                    return IPGetterHelper.getIP(request, forwardProxyHeaderNames);
                }
            });
        }
    }
}
