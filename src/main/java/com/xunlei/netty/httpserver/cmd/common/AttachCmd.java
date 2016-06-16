package com.xunlei.netty.httpserver.cmd.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.component.TimeoutInterrupter;
import com.xunlei.netty.httpserver.component.XLContextAttachment;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.util.StringHelper;

@Service
public class AttachCmd extends BaseStatCmd {

    @Autowired
    private TimeoutInterrupter timeoutInterrupter;

    @CmdAdmin(reportToArmero = true)
    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        StringBuilder tmp = initWithTime(request, response);
        long now = System.currentTimeMillis();
        List<XLContextAttachment> attachs = new ArrayList<XLContextAttachment>(timeoutInterrupter.getLiveAttach());
        Collections.sort(attachs);
        tmp.append("liveAttachs:[").append(attachs.size()).append("]\n");
        for (XLContextAttachment a : attachs) {
            tmp.append(StringHelper.printLine(100, '-'));
            a.getDetailInfo(tmp, now);
        }
        return tmp;
    }
}
