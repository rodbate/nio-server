package com.xunlei.netty.httpserver;

import static org.jboss.netty.channel.Channels.pipeline;
import java.util.NoSuchElementException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.xunlei.netty.httpserver.component.AbstractPageDispatcher;
import com.xunlei.netty.httpserver.component.NOPDispatcher;
import com.xunlei.netty.httpserver.component.XLHttpChunkAggregator;
import com.xunlei.netty.httpserver.component.XLHttpRequestDecoder;
import com.xunlei.netty.httpserver.component.XLHttpResponseEncoder;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.spring.AfterConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;

/**
 * <pre>
 * 一个Boss负责一个端口,如80端口
 * 其一直从端口中通过selector拿到OP_ACCEPT的请求
 * 
 *   SocketChannel acceptedSocket = channel.socket.accept();
 *                     if (acceptedSocket != null) {
 *                         registerAcceptedChannel(acceptedSocket, currentThread);
 *                     }
 * 拿到acceptedSocket后,把这个任务分配给nextWorker
 * 
 * NioWorker worker = nextWorker();
 *                 worker.register(new NioAcceptedSocketChannel(
 *                         channel.getFactory(), pipeline, channel,
 *                         NioServerSocketPipelineSink.this, acceptedSocket,
 *                         worker, currentThread), null);
 * 
 * (扔到了registerTaskQueue上)
 * 
 * selector.wakeup();是什么意思？
 * worker启动后,会一直执行三个命令:
 *   processRegisterTaskQueue();  
 *     --> run一个RegisterTask(没有用独立的线程,直接是 Runnable.run()来执行的)
 *   processWriteTaskQueue();
 *     --> run一个writeTaskQueue(没有用独立的线程,直接是 Runnable.run()来执行的)
 *     --> 内部是执行nioWorker.writeFromTaskLoop
 *   processSelectedKeys(selector.selectedKeys());
 *     --> 判断selectedKeys里面是read的话,则read(SelectionKey),也就是OP_READ
 *     --> 判断selectedKeys里面是write的话,则writeFromSelectorLoop(k),也就是OP_WRITE
 *      -->总逻辑是 先读再写,读不到则不用写
 * </pre>
 * 
 * @author ZengDong
 * @since 2010-5-25 上午08:46:42
 */
@Component
public class HttpServerPipelineFactory implements ChannelPipelineFactory {

    public class SimpleIdleStateAwareChannelHandler extends IdleStateAwareChannelHandler {

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) {
            log.debug("channel:{},idleState:{}", e.getChannel(), e.getState());
            e.getChannel().close();
        }
    }

    private static final Logger log = Log.getLogger();
    @Autowired
    private Bootstrap bootstrap;
    @Autowired
    private HttpServerConfig config;
    /** 上传aggregator大小，单位是字节，默认1M */
    @Config
    private int maxContentLength = 1048576;

    // 2012-03-22 HttpMessageEncoder 里面有带 volatile chunk变量，不能是单例；但是 nettyHttpServer现在一直没有 chunk的resp，所以不怕
    // TODO:特别注意 如果要使用： incubation.com.xunlei.netty.httpserver.cmd 中的 chunk功能，就要特别处理这里
    private HttpResponseEncoder encoder = new XLHttpResponseEncoder();

    // 2012-05-31 使用chunkAggeator来聚合chunk信息，而不是原来的basePageDispatcher来弄(basePageDispatcher是业务层，前面放置了线程池，为了性能考虑，线程池后面的Handler处理事件是不考虑顺序的)
    // 2012-05-31 把原来netty的HttpChunkAggregator 优化成单例的
    private XLHttpChunkAggregator chunkAggeator;

    private ExecutionHandler executionHandler;
    // ---以下为备选项,未真正用到 ---
    private IdleStateAwareChannelHandler idleAwareHandler;
    private Timer idleCheckTimer;
    @Autowired
    private NOPDispatcher nopDispatcher;
    @Autowired
    private AbstractPageDispatcher pageDispatcher;

    // 备份
    public ChannelPipeline _getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        boolean https = false;
        boolean chunks = false;
        boolean idle_check = true;
        if (https) {
            // SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
            // engine.setUseClientMode(false);
            // pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast("decoder", new XLHttpRequestDecoder(config.getStatistics(), config));
        if (chunks) {
            pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        }
        pipeline.addLast("encoder", encoder);
        if (idle_check) {
            // pipeline.addLast("idleStateHandler", new IdleStateHandler(getIdleCheckTimer(), config.readerIdleTimeSeconds, config.writerIdleTimeSeconds, config.allIdleTimeSeconds));
            pipeline.addLast("idleAwareHandler", getIdleAwareHandle());
        }
        pipeline.addLast("pageDispatcher", pageDispatcher);
        if (log.isDebugEnabled()) {
            log.debug("pipeline#{}:{}", pipeline.hashCode(), pipeline);
        }
        return pipeline;
    }

    public IdleStateAwareChannelHandler getIdleAwareHandle() {
        if (idleAwareHandler == null) {
            idleAwareHandler = new SimpleIdleStateAwareChannelHandler();
        }
        return idleAwareHandler;
    }

    public Timer getIdleCheckTimer() {
        if (idleCheckTimer == null) {
            idleCheckTimer = new HashedWheelTimer();
        }
        return idleCheckTimer;
    }

    // @Override
    // public ChannelPipeline getPipeline() throws Exception {
    // ChannelPipeline pipeline = pipeline();
    //
    // for (Map.Entry<String, ChannelHandler> e : currentPipelineMap.entrySet()) {
    // pipeline.addLast(e.getKey(), e.getValue());
    // }
    // pipeline.replace("DECODER", "decoder", new XLHttpRequestDecoder(config.getStatistics(), config));
    // return pipeline;
    // }

    @AfterConfig
    public void init() {
        // 2012-05-31 BasePageDispatcher 已改成支持 MemoryAwareThreadPoolExecutor
        executionHandler = new ExecutionHandler(config.getPipelineExecutorUnordered());
        chunkAggeator = new XLHttpChunkAggregator(maxContentLength);
    }

    public void addSslHandler(ChannelPipeline pipeline) {
    }

    /**
     * <pre>
     * 									 Server
     *  				   LAST----------------------------LAST
     *  					^		pageDispatcher			|
     * 						|		idleChannelHandler		|
     * UpStream(接收请求)	|		encoder					|	DownStream(发送响应)
     * 						|		decoder					|
     * 						|		executor				V
     * 					  FIRST---------------------------FIRST
     * 								  	 Client
     * </pre>
     */
    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();

        if (bootstrap.isStop()) {
            pipeline.addLast("nop", nopDispatcher);// TODO:正在关闭了,现直接简单处理内部什么逻辑都不走
            return pipeline;
        }
        addSslHandler(pipeline);
        // 要注意,并不是说 编码工作 怎么安排都会最后跟业务处理在一个线程中

        // NioWorker.write0(Channel) 方法中,是由 writeFromSelectorLoop(SelectionKey),writeFromTaskLoop(Channel),writeFromUserCode(Channel) 来决定的

        // 如果executor放在最first位置,则编码,解码的工作也是则executor来完成,如果放在encoder,decoder后面,则编码解码工作是用NioRorker来完成的,
        // 我们清楚nioWorker个数是启动已经定好了,一般情况下是 cpu个数的2倍,而且其顺序是则里面的queque来维护的

        // 测试发现 用executor放在 前面,可以减轻nioworker的编解码任务,从而能够让 编码解码互不干扰(让nioworker来实现的话,如果编码慢,也会连累到解码过程,因为此时nioworker大部分在解决编码过程,打嗝了)

        // ab -k -n 100000 -c 1000 "http://192.168.14.199:8927/test/length?len=10240"
        // 让executor来完成编码解码工作时,发现 线程池中queueSize最高峰可以达到 969
        pipeline.addLast("decoder", new XLHttpRequestDecoder(config.getStatistics(), config)); // 这个在每次新建请求时,还需要再建
        pipeline.addLast("aggregator", chunkAggeator);
        pipeline.addLast("encoder", encoder);
        if (config.isPlAddDefalter()) {
            pipeline.addLast("deflater", new HttpContentCompressor());// HttpContentCompressor 不是线程安全的 // 2012-04-18 注意这里现在为了实现可判断包体长度还实时 设置是否gzip功能，是引的自己的包
        }
        pipeline.addLast("pageDispatcher", pageDispatcher);

        // A. 让NioWorker来负责解码工作
        // B. 解码工作也让ExecutionHandler中的线程池来处理
        String addBefore = config.getPlAddBefore();
        if (addBefore != null) {
            if (addBefore.isEmpty()) {
                pipeline.addFirst("executor", executionHandler); // TODO: 2012-05-31 这里严重注意，因为现在线程池改成了不考虑事件顺序，所以不要把线程池放到 decoder及aggeator前（decoder及aggeator是对事件敏感的）
            } else {
                try {
                    pipeline.addBefore(addBefore, "executor", executionHandler);
                } catch (NoSuchElementException e) {// 参数配置错误
                    log.error("config error,config.plAddBefore:{} NOT FOUND,execption:{}", addBefore, e);
                    pipeline.addFirst("executor", executionHandler);
                }
            }
        }
        // if (log.isDebugEnabled())
        // log.debug("pipeline#{}:{}", pipeline.hashCode(), pipeline);
        return pipeline;
    }

    // private Map<String, ChannelHandler> currentPipelineMap;

    /**
     * 重建pipeline顺序
     */
    public HttpServerPipelineFactory rebuildPipeline() {
        return this;
        // if (bootstrap == null || config == null)
        // return;
        // ChannelPipeline pipeline = initPipeline();
        // Map<String, ChannelHandler> pipelineMap = pipeline.toMap();
        // log.error("CurrentPipelineMap[{}]:{}", pipelineMap.size(), pipelineMap);
        // this.currentPipelineMap = pipelineMap;
    }
}
