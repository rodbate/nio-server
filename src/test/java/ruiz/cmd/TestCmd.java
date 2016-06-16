package ruiz.cmd;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.jboss.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.handler.TextResponseHandlerManager;

@Service
public class TestCmd extends BaseCmd {

    @Autowired
    protected TextResponseHandlerManager localHttpServerResponseHandlerManager;

    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.json);
        InputStream is = getClass().getClassLoader().getResourceAsStream("test/ruiz/cmd/json.txt");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int r = is.read();
            if (r == -1) {
                break;
            }
            baos.write(r);
        }
        final String str = new String(baos.toByteArray(), CharsetUtil.UTF_8);
        System.out.println(str);
        final XLContextAttachment attach = response.getAttach();
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                localHttpServerResponseHandlerManager.writeResponse(attach, str);
            }
        }).start();
        return AsyncProxyHandler.ASYNC_RESPONSE;
    }
}
