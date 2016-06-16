package com.xunlei.netty.httpserver.util;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.springframework.stereotype.Service;
import com.xunlei.spring.Config;
import com.xunlei.util.concurrent.NamedThreadFactory;

/**
 * <pre>
 * 参考：http://stackoverflow.com/questions/8444267/how-to-write-a-high-performance-netty-client
 * 
 * 
 * Setting the writeBufferHighWaterMark of the channel to optimal value (Make sure that setting a big value will not create congestion)
 * bootstrap.setOption("writeBufferHighWaterMark", 10 * 64 * 1024);
 * 
 * Setting the SO_READ, SO_WRITE buffer size
 * bootstrap.setOption("sendBufferSize", 1048576); bootstrap.setOption("receiveBufferSize", 1048576);
 * 
 * Enabling the TCP No delay
 * bootstrap.setOption("tcpNoDelay", true);
 * 
 * 
 * @author 曾东
 * @since 2012-5-31 下午1:16:38
 */
@Service
public final class NioSocketBootstrapFactory {

    private static final int DEFAULT_CONFIG_CONNECTTIMEOUTMILLIS = 5000;
    private static final int DEFAULT_CONFIG_RECEIVEBUFFERSIZE = 8192;
    private static final boolean DEFAULT_CONFIG_REUSEADDRESS = true;
    private static final int DEFAULT_CONFIG_SENDBUFFERSIZE = 8192;
    private static final int DEFAULT_CONFIG_SOLINGER = -1;
    private static final boolean DEFAULT_CONFIG_TCPKEEPALIVE = true;
    private static final boolean DEFAULT_CONFIG_TCPNODELAY = true;

    @Config
    private static int workerCount = HttpServerConfig.CORE_PROCESSOR_NUM;// TODO:暂时使用默认的配置,后期考虑外放
    @Config
    private static int plCorePoolSize = HttpServerConfig.CORE_PROCESSOR_NUM * 50;
    @Config
    private static long plKeepAliveSecond = 60l;
    @Config
    private static long plMaxChannelMemorySize = 100 * 1024 * 1024;
    @Config
    private static long plMaxTotalMemorySize = 1024 * 1024 * 1024;// TODO:暂时使用默认的配置,后期考虑外放

    public static ExecutionHandler getExecutionHandler(String name, boolean ordered) {
        return ordered ? getExecutionHandlerOrdered(name) : getExecutionHandlerUnordered(name);
    }

    public static ExecutionHandler getExecutionHandlerUnordered(String name) {
        return new ExecutionHandler(new MemoryAwareThreadPoolExecutor(plCorePoolSize, plMaxChannelMemorySize, plMaxTotalMemorySize, plKeepAliveSecond, TimeUnit.SECONDS, new NamedThreadFactory(
                "PIPELINE-" + name + "#", Thread.NORM_PRIORITY + 2)));
    }

    public static ExecutionHandler getExecutionHandlerOrdered(String name) {
        return new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(plCorePoolSize, plMaxChannelMemorySize, plMaxTotalMemorySize, plKeepAliveSecond, TimeUnit.SECONDS, new NamedThreadFactory(
                "PIPELINE-" + name + "#", Thread.NORM_PRIORITY + 2)));
    }

    public static ClientBootstrap newClientBootstrap(ChannelPipelineFactory channelPipelineFactory, String name) {
        return newClientBootstrap(channelPipelineFactory, name, workerCount);
    }

    public static ClientBootstrap newClientBootstrap(ChannelPipelineFactory channelPipelineFactory, String name, int workerCount) {
        ExecutorService bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O client boss-" + name + "$", Thread.MAX_PRIORITY));
        ExecutorService workerExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O client worker-" + name + "$", Thread.NORM_PRIORITY + 4));
        ChannelFactory channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor, workerCount);
        ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        if (channelPipelineFactory != null) {
            bootstrap.setPipelineFactory(channelPipelineFactory);// 可以在外部来设置
        }
        setBootstrapOptions(bootstrap);// TODO:暂时使用默认的配置,后期考虑外放
        return bootstrap;
    }

    public static ServerBootstrap newServerBootstrap(ChannelPipelineFactory channelPipelineFactory, String name, int listen_port) {
        NetUtil.checkSocketPortBind(listen_port);
        ExecutorService bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O server boss-" + name + "$", Thread.MAX_PRIORITY));
        ExecutorService workerExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("New I/O server worker-" + name + "$", Thread.NORM_PRIORITY + 4));
        ChannelFactory channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor, workerCount);
        ServerBootstrap serverBootstrap = new ServerBootstrap(channelFactory);
        serverBootstrap.setPipelineFactory(channelPipelineFactory);

        setBootstrapOptions(serverBootstrap);// TODO:暂时使用默认的配置,后期考虑外放
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

        return serverBootstrap;
    }

    public static void setBootstrapOptions(Bootstrap bootstrap) {
        setBootstrapOptions(bootstrap, DEFAULT_CONFIG_CONNECTTIMEOUTMILLIS, DEFAULT_CONFIG_RECEIVEBUFFERSIZE, DEFAULT_CONFIG_SENDBUFFERSIZE);
    }

    public static void setBootstrapOptions(Bootstrap bootstrap, int connectTimeoutMillis, int receiveBufferSize, int sendBufferSize) {
        bootstrap.setOption("tcpNoDelay", DEFAULT_CONFIG_TCPNODELAY);
        bootstrap.setOption("soLinger", DEFAULT_CONFIG_SOLINGER);
        bootstrap.setOption("reuseAddress", DEFAULT_CONFIG_REUSEADDRESS);

        bootstrap.setOption("child.tcpNoDelay", DEFAULT_CONFIG_TCPNODELAY); // 设置为非延迟发送，为true则不组装成大包发送，收到东西马上发出
        bootstrap.setOption("child.soLinger", DEFAULT_CONFIG_SOLINGER);
        bootstrap.setOption("child.reuseAddress", DEFAULT_CONFIG_REUSEADDRESS);// 设置每一个非主监听连接的端口可以重用

        bootstrap.setOption("keepAlive", DEFAULT_CONFIG_TCPKEEPALIVE);
        bootstrap.setOption("connectTimeoutMillis", connectTimeoutMillis);
        bootstrap.setOption("receiveBufferSize", receiveBufferSize);// 设置输入缓冲区的大小
        bootstrap.setOption("sendBufferSize", sendBufferSize);// 设置输出缓冲区的大小

        bootstrap.setOption("child.keepAlive", DEFAULT_CONFIG_TCPKEEPALIVE);
        bootstrap.setOption("child.connectTimeoutMillis", connectTimeoutMillis);
        bootstrap.setOption("child.receiveBufferSize", receiveBufferSize);// 设置输入缓冲区的大小
        bootstrap.setOption("child.sendBufferSize", sendBufferSize);// 设置输出缓冲区的大小
    }
}
