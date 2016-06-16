package com.xunlei.netty.httpserver.https;

import javax.net.ssl.SSLEngine;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.ssl.SslHandler;
import org.springframework.stereotype.Component;
import com.xunlei.netty.httpserver.HttpServerPipelineFactory;

/**
 * <pre>
 * 当前netty的https用chrome时，会报错：
 * https://github.com/netty/netty/issues/232
 * 
 * http://code.google.com/p/chromium/issues/detail?id=118366
 * 
 * 
 * 另参考：
 * https://github.com/bblfish/Play20/
 * 
 * http://stackoverflow.com/questions/8731157/netty-https-tls-session-duration-why-is-renegotiation-needed
 * @author ZengDong
 * @since 2012-3-21 下午1:19:40
 */
@Component
public class HttpsServerPipelineFactory extends HttpServerPipelineFactory {

    @Override
    public void addSslHandler(ChannelPipeline pipeline) {
        SSLEngine engine = SslContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);
        pipeline.addLast("ssl", new SslHandler(engine));
    }

}
