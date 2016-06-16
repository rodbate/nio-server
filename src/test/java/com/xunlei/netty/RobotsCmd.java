package com.xunlei.netty;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.util.Log;

/**
 * <pre>
 * 服务器对爬虫的处理策略
 * 
 * 默认设置禁止所有的爬虫，如有特殊需要只需放置robots.txt到classpath下即可
 * </pre>
 * 
 * @since 2011-2-15
 * @author hujiachao
 */
@Service
@CmdMapper(RobotsCmd.robotFileName)
public class RobotsCmd extends BaseCmd {

    public static final String robotFileName = "robots.txt";
    private static final String defaultRobots = "User-agent: *\nDisallow: /\n";
    private static final Logger log = Log.getLogger();

    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        response.setContentCharset(Charset.forName("utf8"));
        URL url = getClass().getClassLoader().getResource(robotFileName);
        if (null != url) {
            try {
                File file = new File(url.toURI());
                FileInputStream fis = new FileInputStream(file);
                int tmp;
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                while ((tmp = fis.read()) != -1) {
                    bao.write(tmp);
                }
                response.setContent(ChannelBuffers.copiedBuffer(bao.toByteArray()));
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return defaultRobots;
    }
}
