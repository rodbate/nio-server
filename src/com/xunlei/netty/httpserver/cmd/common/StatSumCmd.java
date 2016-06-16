package com.xunlei.netty.httpserver.cmd.common;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.cmd.CmdMappers;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.cmd.CmdOverride;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin.CmdAdminType;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.handler.TextResponseHandlerManager;
import com.xunlei.netty.httpserver.util.IPAuthenticator;
import com.xunlei.util.StringHelper;

/**
 * 把常用的一些管理统计查询接口，通过配置，放到统一的一个页面上展示
 * 
 * @author 曾东
 * @since 2012-11-21 下午1:51:48
 */
@Service
public class StatSumCmd extends BaseCmd {

    private List<CmdMeta> cmdsList;
    private List<String> pathList;

    public List<CmdMeta> getCmdsList() {
        if (cmdsList == null) {
            List<CmdMeta> cmdsListTmp = new ArrayList<CmdMeta>();
            pathList = new ArrayList<String>();
            Map<CmdMeta, List<String>> cmd_urls_map = cmdMappers.getReverseCmdAllSortedMap();
            for (Entry<CmdMeta, List<String>> e : cmd_urls_map.entrySet()) {
                CmdMeta meta = e.getKey();
                if (!meta.isDisable()) {
                    CmdAdmin cm = meta.getMethod().getAnnotation(CmdAdmin.class);
                    if (cm != null) {
                        cmdsListTmp.add(meta);
                        pathList.add(e.getValue().get(0));
                    }
                }
            }
            cmdsList = cmdsListTmp;
        }
        return cmdsList;
    }

    @Autowired
    private CmdMappers cmdMappers;
    @Autowired
    private TextResponseHandlerManager handlerManager;

    @CmdMapper("/stat/cmds")
    @CmdOverride
    @CmdAdmin(reportToArmero = false)
    public Object cmds(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        IPAuthenticator.auth(this, request);
        List<CmdMeta> cmdsList = getCmdsList();
        int index = request.getParameterInteger("exeIndex", -1);
        if (index == -1) {// 显示出页面
            response.setInnerContentType(ContentType.html);
            StringBuilder tmp = new StringBuilder();
            tmp.append("<html><body><table><tbody>\n");

            for (int i = 0; i < cmdsList.size(); i++) {
                CmdMeta meta = cmdsList.get(i);
                CmdAdmin ca = meta.getMethod().getAnnotation(CmdAdmin.class);// 不可能是空

                String a = ca.type() == CmdAdminType.OPER ? "*" : "&nbsp;";
                String b = ca.reportToArmero() ? "#" : "&nbsp;";
                // http://blog.sina.com.cn/s/blog_66392eb80100mq82.html 颜色代码
                // http://tool.webmasterhome.cn/html-color.asp
                String style = ca.reportToArmero() ? "" : ca.type() == CmdAdminType.OPER ? "style=\"color:#6B8E23\"" : "style=\"color:#808080\"";

                String m = a + b + pathList.get(i).toString();
                // tmp.append(String.format("<tr><td><a target=\"_blank\" href=\"?exeIndex=%s\">%s</a></td></tr>\n", i, m));
                tmp.append(String.format("<tr><td><a %s href=\"?exeIndex=%s\">%s</a></td></tr>\n", style, i, m));
            }
            tmp.append("</tbody></table></body></html>");
            return tmp.toString();
        }
        StringBuilder info = new StringBuilder();
        return invokeCmd(info, cmdsList.get(index), request, response);
    }

    @CmdMapper("/stat/sum")
    @CmdOverride
    @CmdAdmin(reportToArmero = true)
    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        StringBuilder info = new StringBuilder();
        response.setInnerContentType(ContentType.plain);
        List<CmdMeta> cmdsList = getCmdsList();
        for (CmdMeta meta : cmdsList) {
            if (!StatSumCmd.class.isAssignableFrom(meta.getCmd().getClass())) { // 避免死循环
                CmdAdmin ca = meta.getMethod().getAnnotation(CmdAdmin.class);// 不可能是空
                if (ca.type() == CmdAdminType.STAT && ca.reportToArmero()) {
                    invokeCmd(info, meta, request, response);
                }
            }
        }
        return info;
    }

    private StringBuilder invokeCmd(StringBuilder info, CmdMeta meta, XLHttpRequest request, XLHttpResponse response) throws Exception {
        // info.append(StringHelper.printLine(160, '-'));
        // info.append(meta).append("\n");
        // info.append(StringHelper.printLine(160, '-'));
        info.append(StringHelper.emphasizeTitle(meta.toString(), '+', '=', '|'));
        BaseCmd cmd = meta.getCmd();
        Method method = meta.getMethod();
        Object cmdReturnObj = null;
        try {
            cmdReturnObj = method.invoke(cmd, request, response).toString();
        } catch (Throwable e) {
            cmdReturnObj = handlerManager.handleThrowable(response.getAttach(), e);
        }
        String cmdReturnStr = handlerManager.getContentString(response.getAttach(), cmdReturnObj);
        info.append(cmdReturnStr).append("\n");
        return info;
    }
}
