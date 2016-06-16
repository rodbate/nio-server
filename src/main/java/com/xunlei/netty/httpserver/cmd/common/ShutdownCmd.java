package com.xunlei.netty.httpserver.cmd.common;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.Bootstrap;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin.CmdAdminType;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.netty.httpserver.util.IPAuthenticator;
import com.xunlei.netty.httpserver.util.IPGetterHelper;
import com.xunlei.util.Log;

/**
 * @author ZengDong
 * @since 2010-5-23 上午12:15:48
 */
@Service
public class ShutdownCmd extends BaseCmd {

    private static final Logger log = Log.getLogger();
    @Autowired
    private Bootstrap bootstrap;

    // public static final String GOODBYE = "GOODBYE";
    public static final int shutdownWaitTime = 500;

    /**
     * 关闭服务器
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object process(final XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        final String ip = IPGetterHelper.getIP(request);
        log.error("{} try to shutdown", ip);
        IPAuthenticator.authLocalhost(request);
        Runnable run = new Runnable() {

            @Override
            public void run() {
                log.error("{} SHUTDOWN HTTP SERVER....", ip);
                try {
                    Thread.sleep(shutdownWaitTime);// 给返回此次响应一些时间
                } catch (InterruptedException e) {
                }
                long before = System.currentTimeMillis();
                try {
                    bootstrap.stop();
                } finally {
                    long span = System.currentTimeMillis() - before;
                    String msg = "SHUTDOWN HTTP SERVER DONE...USING " + span + "MS";
                    System.err.println(msg);
                    HttpServerConfig.ALARMLOG.error("{}->{}", ip, msg);
                    System.exit(347);// 这里定义一个特殊的退出状态码,表示正常退出
                }
            }
        };
        Thread t = new Thread(run);
        t.start();
        return System.currentTimeMillis() + shutdownWaitTime + ""; // 返回给 新svr，当前svr开始exit的时间
    }
}
