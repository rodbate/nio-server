package com.xunlei.netty.httpserver.util;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import com.xunlei.util.concurrent.NamedThreadFactory;

/**
 * <pre>
 * 简单封装nioServerSocket启动服务的一般流程
 * 
 * 请改用 NioSocketBootstrapFactory
 * 
 * @author ZengDong
 * @since 2011-4-16 下午02:19:05
 */
@Deprecated
public class NioServerSocketBootstrap {

    private static int plCorePoolSize = HttpServerConfig.CORE_PROCESSOR_NUM * 50;
    private static long plKeepAliveSecond = 60l;
    private static long plMaxChannelMemorySize = 100 * 1024 * 1024;
    private static long plMaxTotalMemorySize = 1024 * 1024 * 1024;// TODO:暂时使用默认的配置,后期考虑外放

    public static ExecutionHandler getExecutionHandler(String name) {
        return new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(plCorePoolSize, plMaxChannelMemorySize, plMaxTotalMemorySize, plKeepAliveSecond, TimeUnit.SECONDS, new NamedThreadFactory(
                "PIPELINE-" + name + "#", Thread.NORM_PRIORITY + 2)));
    }

    private ExecutorService bossExecutor;
    private ChannelPipelineFactory channelPipelineFactory;
    private int listen_port;

    private String name;
    private ServerBootstrap serverBootstrap;
    private ServerSocketChannelFactory serverSocketChannelFactory;
    private int workerCount = HttpServerConfig.CORE_PROCESSOR_NUM * 2;// TODO:暂时使用默认的配置,后期考虑外放

    private ExecutorService workerExecutor;

    public NioServerSocketBootstrap(ChannelPipelineFactory channelPipelineFactory, int listen_port, String name) {
        this.channelPipelineFactory = channelPipelineFactory;
        this.listen_port = listen_port;
        this.name = name;
        this.bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O server boss-" + name + "$", Thread.MAX_PRIORITY));
        this.workerExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O server worker-" + name + "$", Thread.NORM_PRIORITY + 4));
    }

    public ChannelPipelineFactory getChannelPipelineFactory() {
        return channelPipelineFactory;
    }

    public int getListen_port() {
        return listen_port;
    }

    public String getName() {
        return name;
    }

    public ServerBootstrap getServerBootstrap() {
        return serverBootstrap;
    }

    public ServerSocketChannelFactory getServerSocketChannelFactory() {
        return serverSocketChannelFactory;
    }

    public void start() {
        NetUtil.checkSocketPortBind(listen_port);
        serverSocketChannelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor, workerCount);
        serverBootstrap = new ServerBootstrap(serverSocketChannelFactory);
        serverBootstrap.setPipelineFactory(channelPipelineFactory);

        NioSocketBootstrapFactory.setBootstrapOptions(serverBootstrap);// TODO:暂时使用默认的配置,后期考虑外放
        Exception ex = null;
        try {
            Channel c = serverBootstrap.bind(new InetSocketAddress(listen_port));// Bind and start to accept incoming connections.
        } catch (ChannelException e) {
            ex = e;
        }

        String msg = ex == null ? "OK" : "ERROR";
        String chnmsg = ex == null ? "NioServerSocket-" + name + "服务启动完毕.(port[" + listen_port + "])" : "NioServerSocket-" + name + "服务启动失败.(port[" + listen_port + "])";

        String errStr = ex == null ? "" : ex.getMessage();
        HttpServerConfig.ALARMLOG.error("NioServerSocket-{}(port[{}],workerCount[{}]) Start {}.", new Object[] {
            name,
            listen_port,
            workerCount,
            msg,
            ex
        });
        System.err.println(chnmsg + errStr);

        if (ex != null) {
            System.exit(1);
        }
    }
}
