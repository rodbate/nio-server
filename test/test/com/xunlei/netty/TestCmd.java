package test.com.xunlei.netty;

import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.util.IPAuthenticator;

/**
 * @author ZengDong
 * @since 2010-3-18 下午01:43:24
 */
@Service
public class TestCmd extends BaseCmd {

    public Object timeout(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        Thread.sleep(Integer.MAX_VALUE);
        return null;
    }

    public Object auth(XLHttpRequest request, XLHttpResponse response) throws Exception {
        IPAuthenticator.auth(request);
        return null;
    }

    public Object error(XLHttpRequest request, XLHttpResponse response) throws Exception {
        // response.setInnerContentType(ContentType.plain);
        throw new Exception("test throw exception");
    }

}
