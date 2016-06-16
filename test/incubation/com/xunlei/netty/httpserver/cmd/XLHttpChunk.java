package incubation.com.xunlei.netty.httpserver.cmd;

import java.nio.charset.Charset;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import com.xunlei.util.CharsetTools;

/**
 * @author ZengDong
 * @since 2011-3-18 下午02:02:22
 */
public class XLHttpChunk extends DefaultHttpChunk {

    public XLHttpChunk(String contentStr, Charset contentCharset) {
        super(ChannelBuffers.copiedBuffer(contentStr, contentCharset));
    }

    public XLHttpChunk(String contentStr) {
        super(ChannelBuffers.copiedBuffer(contentStr, CharsetTools.UTF_8));
    }
}
