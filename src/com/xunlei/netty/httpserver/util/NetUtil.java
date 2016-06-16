package com.xunlei.netty.httpserver.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.exception.ClosedChannelError;
import com.xunlei.netty.httpserver.exception.ProcessFinishedError;
import com.xunlei.util.CloseableHelper;
import com.xunlei.util.Log;

/**
 * @author ZengDong
 * @since 2011-4-15 下午10:13:22
 */
public class NetUtil {

    private static Logger log = Log.getLogger();

    /**
     * 校验端口是否被占用,如果被占用程序退出
     */
    public static void checkSocketPortBind(boolean exitWhenError, int... ports) {
        if (System.getProperty("os.name").startsWith("Win")) {
            // 在linux中不用判断是否被占用
            for (int port : ports) {
                if (port > 0) {
                    boolean isBind = true;
                    Socket socket = null;
                    try {
                        socket = new Socket("localhost", port);
                    } catch (ConnectException e1) {
                        isBind = false;
                    } catch (Exception e1) {
                        log.error("", e1);
                        isBind = true;
                    } finally {
                        CloseableHelper.closeSilently(socket);
                    }
                    if (isBind) {
                        String errStr = "Failed to bind to " + port;
                        if (exitWhenError) {
                            System.err.println(errStr);
                            HttpServerConfig.ALARMLOG.error(errStr);
                            System.exit(1);
                        } else {
                            throw new RuntimeException(errStr);
                        }
                    }
                }
            }
        }
    }

    /**
     * 校验端口是否被占用,如果被占用程序退出
     */
    public static void checkSocketPortBind(int... ports) {
        checkSocketPortBind(false, ports);
    }

    /**
     * <pre>
     * 屏蔽以下异常：
     * 远程主机强迫关闭了一个现有的连接,Connection reset by peer
     * 
     * 已知不屏蔽的异常有：
     * 您的主机中的软件中止了一个已建立的连接(windows中控制最大连接数造成的)
     * </pre>
     */
    // private String resetByPeerFilterStr = Locale.getDefault().equals(Locale.CHINA) ? "强迫关闭" : "reset by";
    private static String resetByPeerFilterStr = "reset by";
    private static String resetByPeerFilterStr1 = "强迫关闭";
    private static String connectionTimedOut = "Connection timed out";

    public static boolean exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        return exceptionCaught(ctx, e, "");
    }

    /**
     * 处理通用的exception
     */
    public static boolean exceptionCaught(ChannelHandlerContext ctx, Throwable t, String type) throws Exception {
        boolean channelInterrupt = false;
        try {
            Channel channel = ctx.getChannel();
            Object attach = ctx.getAttachment();
            Object v = attach == null ? channel : attach;// 如果有attach,就打印attach
            if (t != null) {
                if (t instanceof IOException && t.getMessage() != null) {
                    String msg = t.getMessage();
                    if (msg.contains(resetByPeerFilterStr) || msg.contains(resetByPeerFilterStr1)) {
                        log.info("resetByPeer       {}:{},{}", new Object[] {
                            type,
                            v,
                            msg
                        });
                    } else if (msg.contains(connectionTimedOut)) {
                        log.info("connectionTimeout {}:{},{}", new Object[] {
                            type,
                            v,
                            msg
                        });
                    }
                } else if (t instanceof ClosedChannelException || t instanceof ClosedChannelError) {
                    log.info("channelClosed     {}:{},{}", new Object[] {
                        type,
                        v,
                        t.getMessage()
                    });
                    channelInterrupt = true;
                } else if (!(t instanceof ProcessFinishedError)) {
                    log.error("exceptionCaught   {}:{}", new Object[] {
                        type,
                        v,
                        t
                    });
                }
            } else {
                log.error("exceptionCaught  {}:{}", new Object[] {
                    type,
                    v,
                    t
                });
            }
        } catch (Exception e2) {
        }
        return channelInterrupt;
    }

    /**
     * 处理通用的exception
     */
    public static boolean exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e, String type) throws Exception {
        try {
            return exceptionCaught(ctx, e.getCause(), type);
        } finally {
            ctx.sendUpstream(e);
        }
    }

}
