package com.xunlei.armero.cmd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.armero.MonitorFactory;
import com.xunlei.armero.MonitorFactory.MonitorCmdFormater;
import com.xunlei.json.JSONEngine;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.annotation.CmdMonitor;
import com.xunlei.netty.httpserver.component.HeartbeatMessage;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;

@Service
public class HeartbeatCmd extends BaseCmd {

    @Autowired
    private MonitorFactory monitorFactory;

    @CmdMonitor(param = "key=value", userId = 501, contains = {
        "这是一个监控用例的demo,armero自己监控自己并报错发邮件,想关掉这个报错,只要不上报就行了,也就是serverconfig中配置空url:heartbeatUrl="
    }, lengthMin = 0, rtn = -1, status = 200)
    public Object login(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(XLHttpResponse.ContentType.plain);
        String info = request.getParameterCompelled("info");
        HeartbeatMessage msg = JSONEngine.DEFAULT_JACKSON_MAPPER.readValue(info, HeartbeatMessage.class);
        msg.setRemoteIp(request.getRemoteIP()); // 把 远程ip记录下来，以便于 监控回调用
        return monitorFactory.login(msg);
    }

    public Object logout(XLHttpRequest request, XLHttpResponse response) throws Exception {
        String id = request.getParameterCompelled("id");
        this.monitorFactory.logout(id);
        response.redirect("/heartbeat/list");
        return "OK";
    }

    public Object list(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(XLHttpResponse.ContentType.html);
        return MonitorCmdFormater.list(request.getParameter("field"), request.getParameter("value"));
    }
}
