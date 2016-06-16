package com.xunlei.netty.httpserver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Resource;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerStat;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import com.xunlei.netty.httpserver.component.AbstractPageDispatcher;
import com.xunlei.netty.httpserver.https.HttpsServerPipelineFactory;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.netty.httpserver.util.NetUtil;
import com.xunlei.netty.httpserver.util.NioSocketBootstrapFactory;
import com.xunlei.spring.BeanUtil;
import com.xunlei.spring.Config;
import com.xunlei.spring.SpringBootstrap;
import com.xunlei.util.CloseableHelper;
import com.xunlei.util.EmptyChecker;
import com.xunlei.util.Log;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * @author ZengDong
 * @since 2010-3-18 下午12:52:19
 */
@Component
public class Bootstrap {

    /**
     * 请改用 SpringBootstrap.getContext() 或者直接用 BeanUtil的相关快捷方法
     */
    @Deprecated
    public static ApplicationContext CONTEXT;
    private static final Logger log = Log.getLogger();
    private volatile boolean stopping = false;

    public static boolean isArgWhat(String[] args, String... what) {
        if (EmptyChecker.isEmpty(args)) {
            return false;
        }
        String arg = args[0].toLowerCase();
        for (String w : what) {
            if (arg.indexOf(w) >= 0) {
                return true;
            }
        }
        return false;
    }

    public static ApplicationContext main(String[] args, Runnable initialRunnable, Runnable shutdownRunnalbe, String... springConfigLocations) throws IOException {
        long before = System.currentTimeMillis();
        try {
            CONTEXT = com.xunlei.spring.SpringBootstrap.load(springConfigLocations);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1); // 遇到任何异常直接退出
        }
        System.err.println("----->loadContext      [" + (System.currentTimeMillis() - before) + "MS]");
        Bootstrap bootstrap = BeanUtil.getTypedBean("bootstrap");
        bootstrap.shutdownRunanble = shutdownRunnalbe;
        if (isArgWhat(args, "stop", "shutdown")) {
            bootstrap.sendShutdownCmd();
            System.exit(0);
        } else {
            // 2012-05-29
            // Ruiz增加启动参数compelled 在发现需要监听的http端口已经被占用时，会尝试关闭前个Ruiz再来监听此端口，
            // compelled用于 替代原来老的 restart 参数,这种方式 更趋近于 无缝重启（中间重启的间隔更短）
            if (!isArgWhat(args, "compelled", "force")) {
                bootstrap.bindRetryTimeout = 0;
            }
            bootstrap.start(initialRunnable);
        }
        return CONTEXT;
    }

    public static ApplicationContext main(String[] args, Runnable initialRunnable, String... springConfigLocations) throws IOException {
        return main(args, initialRunnable, null, springConfigLocations);
    }

    public static ApplicationContext main(String[] args, String... springConfigLocations) throws IOException {
        return main(args, null, null, springConfigLocations);
    }

    private ExecutorService bossExecutor = HttpServerConfig.bossExecutor;
    private Runnable shutdownRunanble = null;
    @Resource
    private HttpServerPipelineFactory httpServerPipelineFactory;
    @Resource
    private HttpsServerPipelineFactory httpsServerPipelineFactory;
    @Autowired
    private HttpServerConfig config;
    @Autowired
    private AbstractPageDispatcher pageDispatcher;
    private NioServerSocketChannelFactory serverSocketChannelFactory;
    private ExecutorService workerExecutor = HttpServerConfig.workerExecutor;
    @Config
    private long bindRetryTimeout = 60000;// 绑定重试的超时时间，默认60s

    private void initEnv() {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        // 2012-05-29 判断端口是否已经被占用，挪到真正监听时判断
        // int https_port = config.getHttps_listen_port();
        // int port = config.getListen_port();
        // NetUtil.checkSocketPortBind(port, https_port);
    }

    private void initOutter(Runnable initialRunnable) {
        if (initialRunnable != null) {
            long before = System.currentTimeMillis();
            initialRunnable.run();
            System.err.println("----->initialRunnable  [" + (System.currentTimeMillis() - before) + "MS]");
        }
    }

    private ServerBootstrap initServerBootstrap(HttpServerPipelineFactory channelPipelineFactory) {
        ServerBootstrap serverBootstrap = new ServerBootstrap(serverSocketChannelFactory);
        serverBootstrap.setPipelineFactory(channelPipelineFactory.rebuildPipeline());// Set up the event pipeline factory.
        NioSocketBootstrapFactory.setBootstrapOptions(serverBootstrap, config.getConnectTimeoutMillis(), config.getReceiveBufferSize(), config.getSendBufferSize());
        return serverBootstrap;
    }

    private void bind(int port, ServerBootstrap serverBootstrap) {
        if (port > 0) {
            // ServerBootstrap serverBootstrap = new ServerBootstrap(serverSocketChannelFactory);
            // serverBootstrap.setPipelineFactory(channelPipelineFactory.rebuildPipeline());// Set up the event pipeline factory.
            // NioSocketBootstrapFactory.setBootstrapOptions(serverBootstrap, config.getConnectTimeoutMillis(), config.getReceiveBufferSize(), config.getSendBufferSize());
            Channel c = serverBootstrap.bind(new InetSocketAddress(port));// Bind and start to accept incoming connections.
        }
    }

    private void initServer() {
        // workercount 数目不能太多,因为其内部一直在 循环接收 task,占用cpu
        serverSocketChannelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor, config.getRealWorkerCount());
        try {
            NioWorkerStat.registerNioWorkers(serverSocketChannelFactory);// 注册到NioWorkerStat,用于统计
        } catch (Throwable e) {
            log.warn("{}:{}", e.getClass().getSimpleName(), e.getMessage());
        }
        int https_port = config.getHttps_listen_port();
        int port = config.getListen_port();
        String ports = https_port > 0 ? port + "&" + https_port : port + "";
        Exception ex = null;
        try {
            final AtomicBoolean sended = new AtomicBoolean(false);
            final AtomicLong retryBind = new AtomicLong();
            ServerBootstrap serverBootstrap = initServerBootstrap(httpServerPipelineFactory);
            ServerBootstrap httpsServerBootStrap = https_port > 0 ? initServerBootstrap(httpsServerPipelineFactory) : null;
            boolean binded = false;
            long beginBind = System.currentTimeMillis();

            long lastPrintTime = 0;
            while (!binded) {
                try {
                    long thisPrintTime = System.currentTimeMillis();
                    if (thisPrintTime - lastPrintTime > 500) {// 每500ms打印一个信息
                        System.err.println("----->bindHttpPort     [" + ports + "]");
                        lastPrintTime = thisPrintTime;
                    }
                    NetUtil.checkSocketPortBind(port, https_port);
                    bind(port, serverBootstrap);
                    bind(https_port, httpsServerBootStrap);
                    for (Object o : SpringBootstrap.getContext().getBeansOfType(BootstrapOther.class).values()) {
                        System.err.println("----->bootstrapOther");
                        BootstrapOther bootstrapOther = (BootstrapOther) o;
                        bootstrapOther.bind();
                    }

                    long endBind = System.currentTimeMillis();
                    long rb = retryBind.get();
                    if (rb > 0) {// 说明有reBind
                        System.err.println("----->SeamlessLevel    [" + (endBind - rb) + "MS]");
                    }
                    System.err.println("----->bindHttpPort     [" + (endBind - beginBind) + "MS]");
                    binded = true;
                } catch (final Exception e) {
                    if (System.currentTimeMillis() - beginBind > bindRetryTimeout) {
                        throw e;
                    }
                    if (!sended.get()) {
                        ConcurrentUtil.getDaemonExecutor().execute(new Runnable() {

                            @Override
                            public void run() {
                                System.err.println("----->sendShutdownCmd  [" + config.getListen_port() + "]");
                                log.error("{}", e.toString());
                                long oriSvrBeginExitTime = sendShutdownCmd();
                                boolean result = oriSvrBeginExitTime > 0;
                                if (result) {
                                    if (retryBind.get() == 0) {
                                        retryBind.set(oriSvrBeginExitTime);
                                    }
                                }
                                sended.set(result);
                            }
                        });
                        sended.set(true);
                    }
                    ConcurrentUtil.threadSleep(10);
                }
            }
        } catch (Exception e) {
            ex = e;
        }

        long span = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        String msg = ex == null ? "OK" : "ERROR";
        String chnmsg = ex == null ? "服务器启动完毕.(port[" + ports + "])" : "服务器启动失败.(port[" + config.getListen_port() + "])";
        String spanStr = "[" + span + "MS]";
        String errStr = ex == null ? "" : ex.getMessage();
        HttpServerConfig.ALARMLOG.error("HTTPServer(port[{}],workerCount[{}]) Start {}.{}", new Object[] {
            ports,
            config.getRealWorkerCount(),
            msg,
            spanStr,
            ex
        });

        // http://www.kammerl.de/ascii/AsciiSignature.php
        // choose font: doom
        // 还可以的： small

        String ascii_doom = "______  _   _  _____  ______\r\n" + "| ___ \\| | | ||_   _||___  /\r\n"
                + "| |_/ /| | | |  | |     / / \r\n"
                + "|    / | | | |  | |    / /  \r\n"
                + "| |\\ \\ | |_| | _| |_ ./ /___\r\n"
                + "\\_| \\_| \\___/  \\___/ \\_____/";
        System.err.println(ascii_doom + chnmsg + spanStr + errStr);
        // System.err.println(chnmsg + spanStr + errStr);

        if (ex != null) {
            System.exit(1);
        }
    }

    public void start(Runnable initialRunnable) throws IOException {
        initEnv();
        pageDispatcher.init();
        initOutter(initialRunnable);
        initServer();
    }

    public long sendShutdownCmd() {
        HttpURLConnection urlConnection = null;
        LineNumberReader lineReader = null;
        try {
            URL url = new URL("http://localhost:" + config.getListen_port() + "/shutdown");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(10000);
            lineReader = new LineNumberReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String tmp = lineReader.readLine();
                if (tmp == null) {
                    break;
                }
                sb.append(tmp);
            }
            String returnStr = sb.toString();
            log.error("shutdown last result:{}", returnStr);
            return Long.valueOf(returnStr);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
                urlConnection = null;
            }
            CloseableHelper.closeSilently(lineReader);
        }
        return 0;
    }

    public void stop() {
        stopping = true;
        String msg = null;
        httpServerPipelineFactory.rebuildPipeline();
        if (shutdownRunanble != null) {// 再作业务关闭处理
            long before = System.currentTimeMillis();
            msg = "shutdownRunanble run begin...";
            log.error(msg);
            System.err.println(msg);
            shutdownRunanble.run();
            msg = "shutdownRunanble run end,USING " + (System.currentTimeMillis() - before) + " MS";
            log.error(msg);
            System.err.println(msg);
        }

        // 正式环境发现releaseExternalResources要花很久时间
        // 2011-02-16 因此默认不处理这些线程池
        // 如果要release,请在shudown Runnnable 中加上 HttpServerConfig.releaseExternalResources()方法来实现
        // serverBootstrap.releaseExternalResources(); // 先关httpServer
    }

    public boolean isStop() {
        return stopping;
    }
}
