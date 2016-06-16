package com.xunlei.netty.httpserver.component;

import java.lang.reflect.Method;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.async.AsyncProxyHandler;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.exception.ResourceNotFoundError;
import com.xunlei.netty.httpserver.handler.TextResponseHandlerManager;

/**
 * 命令映射分发器
 * 
 * <pre>
 * 约定： http://host:port /cmdname [/methodname] [?callback=value&debug=1]
 * 按cmdname找到指定的cmd类(类名默认以Cmd为后缀)
 * 当指定了methodname时,调用cmd类的对应名称的方法,未指定时调用process方法
 * </pre>
 * 
 * <pre>
 * 通过CmdMapper的映射关系来找到对应命令
 * 在httpserver启动时,dispatcher会初始化cmdMappers里面的所有命令
 * </pre>
 * 
 * @author ZengDong
 * @since 2010-6-8 上午01:53:26
 */
@Service
public class CmdMapperDispatcher extends BasePageDispatcher {

    @Autowired
    protected TextResponseHandlerManager handlerManager;

    // private static final Logger log = Log.getLogger();

    protected Object _dispatch(XLContextAttachment attach) throws Exception {
        XLHttpRequest request = attach.getRequest();
        XLHttpResponse response = attach.getResponse();
        String path = request.getPath();

        CmdMeta meta = cmdMappers.getCmdMeta(path);
        if (meta == null) {
            throw ResourceNotFoundError.INSTANCE;
        }
        // 把attach上设置其CmdMeta
        attach.setCmdMeta(meta);

        if (meta.isDisable()) {
            throw ResourceNotFoundError.INSTANCE;
        }

        BaseCmd cmd = meta.getCmd();
        Method method = meta.getMethod();
        try {
            attach.registerProcessThread();
            // 2011-01-21 都通过反射来调用这些接口
            // if (method == null) {
            // return cmd.process(request, response);
            // } else {
            return method.invoke(cmd, request, response);
            // }
        } finally {
            attach.unregisterProcessThread();
        }
    }

    @Override
    protected void dispatch(XLContextAttachment attach) throws Exception {
        attach.checkChannelOrThread();
        Object cmdReturnObj = null;
        try {
            if (SystemChecker.isDenialOfService()) {
                XLHttpResponse resp = attach.getResponse();
                resp.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE); // 拒绝服务 503
                resp.setInnerContentType(ContentType.plain);
                cmdReturnObj = "Denial Of Service";
            } else {
                cmdReturnObj = _dispatch(attach);
            }
        } catch (Throwable ex) {
            cmdReturnObj = handlerManager.handleThrowable(attach, ex);
        } finally {
            if (cmdReturnObj != AsyncProxyHandler.ASYNC_RESPONSE) {
                handlerManager.writeResponse(attach, cmdReturnObj);
            }
        }
    }

    @Override
    public void init() {
        // 优先级则低到高
        // long before = System.currentTimeMillis();
        cmdMappers.initAutoMap();
        cmdMappers.initCmdMapperDefinedMap();
        cmdMappers.initConfigMap();
        cmdMappers.printFuzzyMap();
        cmdMappers.resetCmdConfig();
        // log.debug("CmdMapperDispatcher init USING {} MS",System.currentTimeMillis() - before);//测试发现初始化不耗时
    }
}
