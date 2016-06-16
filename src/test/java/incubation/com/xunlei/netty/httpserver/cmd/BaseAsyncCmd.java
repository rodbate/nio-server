package incubation.com.xunlei.netty.httpserver.cmd;

import com.xunlei.netty.httpserver.component.XLContextAttachment;

/**
 * 实现 异步命令接口
 * 
 * @author ZengDong
 * @since 2011-3-18 下午05:42:15
 */
public abstract class BaseAsyncCmd {

    /**
     * 异步命令默认调用方法
     */
    public void process(XLContextAttachment attach) throws Exception {
    }
}
