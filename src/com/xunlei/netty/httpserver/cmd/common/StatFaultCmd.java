package com.xunlei.netty.httpserver.cmd.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.async.FaultProcessorUtil;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.cmd.CmdOverride;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;

/**
 * 统计异步消息失败容错处理的结果
 * 
 * @since 2012-12-27
 * @author hujiachao
 */
@Service
public class StatFaultCmd extends BaseStatCmd {

    @Autowired
    private FaultProcessorUtil faultProcessorUtil;

    @CmdOverride
    @CmdMapper("/stat/fault")
    @CmdAdmin(reportToArmero = true)
    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return FaultProcessorUtil.getFaultStatInfo();
    }
}
