package com.xunlei.netty.httpserver.cmd;

/**
 * @author ZengDong
 * @since 2010-3-18 下午01:44:53
 */
public abstract class BaseCmd {

    // /**
    // * 当前命令默认调用方法，有需要请覆盖
    // *
    // * @param request
    // * @param response
    // * @return Object 处理结果,则调用者(CmdDispatcher)对此结果作进一步封装处理,一般是当作response的内容
    // * @throws Exception 抛出异常给调用者(CmdDispatcher)
    // */
    // public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
    // throw ResourceNotFoundError.INSTANCE;
    // }
}
