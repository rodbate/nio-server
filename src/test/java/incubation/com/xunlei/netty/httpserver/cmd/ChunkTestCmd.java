package incubation.com.xunlei.netty.httpserver.cmd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;

/**
 * <pre>
 * 简单测试pagelet 功能实现
 * 
 * http://www.ibm.com/developerworks/cn/web/wa-lo-comet/
 * http://web.archiveorange.com/archive/v/ZVMdIr7yfNn1iJ2cAZQS
 * @author ZengDong
 * @since 2011-3-18 下午02:13:07
 */
@Service
public class ChunkTestCmd extends BaseAsyncCmd {

    @Autowired
    private TextChunkResponseHandler chunkResponseHandler;

    @Override
    public void process(XLContextAttachment attach) throws Exception {
        XLHttpResponse response = attach.getResponse();
        response.setInnerContentType(ContentType.plain);
        // response.setKeepAliveTimeout(10);
        response.addHeader("Server", "XunLei Comet Test Server");
        chunkResponseHandler.writeChunkBegin(attach);

        for (int i = 0; i < 300; i++) {// 这里是直接在当前线程进行模拟streaming,具体情况一般是在另一个线程上处理
            Thread.sleep(20);
            chunkResponseHandler.writeChunk(attach, new XLHttpChunk("push " + i + "\n"));
        }
        chunkResponseHandler.writeChunk(attach, new XLHttpChunk("Bye bye"));
        chunkResponseHandler.writeChunkEnd(attach);
    }
}
