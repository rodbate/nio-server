package com.xunlei.netty.httpserver.cmd.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sun.net.InetAddressCachePolicy;
import com.xunlei.netty.httpserver.HttpServerPipelineFactory;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.cmd.CmdMappers;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.cmd.CmdOverride;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin.CmdAdminType;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.netty.httpserver.util.HttpServerConfig;
import com.xunlei.netty.httpserver.util.IPAuthenticator;
import com.xunlei.netty.httpserver.util.TimeSpanStatHelper;
import com.xunlei.spring.ConfigAnnotationBeanPostProcessor;
import com.xunlei.util.HttpUtil;
import com.xunlei.util.InetAddressCacheUtil;
import com.xunlei.util.Log;
import com.xunlei.util.NumberStringUtil;
import com.xunlei.util.StringTools;
import com.xunlei.util.stat.TimeSpanStat;

/**
 * 实时设置
 * 
 * @author ZengDong
 * @since 2010-5-23 上午12:15:48
 */
@Service
public class SettingCmd extends BaseStatCmd {

    private static final Logger log = Log.getLogger();
    @Autowired
    private CmdMappers cmdMappers;
    @Autowired
    private HttpServerConfig config;
    @Autowired
    private ConfigAnnotationBeanPostProcessor configProcessor;
    @Resource
    private HttpServerPipelineFactory httpServerPipelineFactory;
    @Autowired
    private StatCmd statCmd;
    @Autowired
    private TimeSpanStatHelper timeSpanStatHelper;

    /**
     * <pre>
     * 服务器的所有接口都不被爬虫收录
     * 详情见：http://www.baidu.com/search/robots.html
     * </pre>
     */
    @CmdMapper("/robots.txt")
    @CmdOverride
    @CmdAdmin(reportToArmero = false)
    public Object robots(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        return "User-agent: *\nDisallow: /\n";
    }

    /**
     * <pre>
     * setting/cmds 统计各个接口请求量数据
     * txt 参数为true表示文本展示统计信息
     * last 参数为true表示显示最近10分钟内的请求数据
     * order 参数为true表示根据请求量all_num进行排序显示
     * </pre>
     */
    @CmdAdmin(reportToArmero = false)
    public Object cmds(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        boolean useTxt = request.getParameterBoolean("txt", false);
        boolean timesOrder = request.getParameterBoolean("timesOrder", false);
        boolean avgOrder = request.getParameterBoolean("avgOrder", false);
        boolean last = request.getParameterBoolean("last", true); // 默认展示最近10分钟内数据
        if (!useTxt) {
            response.setInnerContentType(ContentType.html);
        }
        StringBuilder tmp = new StringBuilder();
        Map<CmdMeta, List<String>> cmd_urls_map = cmdMappers.getReverseCmdAllSortedMap();
        Map<CmdMeta, List<String>> last10min_urls_map = null;
        // 选择展示历史或者当前统计数据
        if (last) {
            cmd_urls_map = timeSpanStatHelper.getSettingCmdsTSS().getLatestCmdUrlsMap();
            last10min_urls_map = timeSpanStatHelper.getSettingCmdsTSS().getLast10minCmdUrlsMap();
        }
        Collection<Map.Entry<CmdMeta, List<String>>> entry = cmd_urls_map.entrySet();
        Collection<Map.Entry<CmdMeta, List<String>>> lastEntry = last10min_urls_map != null ? last10min_urls_map.entrySet() : null;
        // 按照各个字段排序
        // 按请求比例排序
        if (timesOrder) {
            ArrayList<Map.Entry<CmdMeta, List<String>>> sorting = new ArrayList<Map.Entry<CmdMeta, List<String>>>(entry);
            Collections.sort(sorting, new Comparator<Map.Entry<CmdMeta, List<String>>>() {

                @Override
                public int compare(Map.Entry<CmdMeta, List<String>> o1, Map.Entry<CmdMeta, List<String>> o2) {
                    return (int) (o2.getKey().getStat().getAllNum() - o1.getKey().getStat().getAllNum()); // 根据方法调用次数的大小排序
                }
            });
            entry = sorting;
        }
        // 按平均请求时长排序
        if (avgOrder) {
            ArrayList<Map.Entry<CmdMeta, List<String>>> sorting = new ArrayList<Map.Entry<CmdMeta, List<String>>>(entry);
            Collections.sort(sorting, new Comparator<Map.Entry<CmdMeta, List<String>>>() {

                @Override
                public int compare(Map.Entry<CmdMeta, List<String>> o1, Map.Entry<CmdMeta, List<String>> o2) {
                    TimeSpanStat o2Stat = o2.getKey().getStat();
                    TimeSpanStat o1Stat = o1.getKey().getStat();
                    long o2AllSpan = o2Stat.getAllSpan();
                    long o2AllNum = o2Stat.getAllNum();
                    long o1AllSpan = o1Stat.getAllSpan();
                    long o1AllNum = o1Stat.getAllNum();
                    return (int) ((o2AllNum == 0 ? 0 : o2AllSpan / o2AllNum) - (o1AllNum == 0 ? 0 : o1AllSpan / o1AllNum)); // 根据平均时长排序
                }
            });
            entry = sorting;
        }
        long allNum = 0;
        for (Entry<CmdMeta, List<String>> e : cmd_urls_map.entrySet()) {
            allNum += e.getKey().getStat().getAllNum(); // 2012-03-27 老的方式：long allNum = statCmd.getProcessTSS().getAllNum() + 1;// +1是因为线程跑到这里时allNum会较cmdNum少一个计数
        }
        long lastAllNum = 0; // 为了统计上一个完整10分钟的统计数据
        if (last10min_urls_map != null) {
            for (Entry<CmdMeta, List<String>> e : last10min_urls_map.entrySet()) {
                lastAllNum += e.getKey().getStat().getAllNum();
            }
        }
        if (useTxt) {
            boolean printHead = true;
            String format = "%-40s %s\n";
            for (Entry<CmdMeta, List<String>> e : entry) {
                CmdMeta meta = e.getKey();
                List<String> url = e.getValue();
                if (printHead) {
                    tmp.append(meta.getStat().getTableHeader());
                    printHead = false;
                }
                long cmdNum = meta.getStat().getAllNum();
                String percentageStr = allNum == 0 || cmdNum == 0 ? "" : NumberStringUtil.DEFAULT_PERCENT.formatByDivide(cmdNum, allNum);
                tmp.append(String.format(format, meta, url));
                tmp.append(meta.getStat().toString(percentageStr));
                tmp.append("\n");
            }
        } else {
            String timeStatFmt = "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n";
            tmp.append("<html><body><table width=\"120%\"><tbody>\n");
            tmp.append(String.format(timeStatFmt, "<a href=\"?last=" + (last ? 0 : 1) + "\">cmd</a>", "<a href=\"?last=" + (last ? 1 : 0)
                    + "&timesOrder="
                    + (timesOrder ? 0 : 1)
                    + "\">times</a>"
                    + (timesOrder ? "↓" : ""), "<a href=\"?last=" + (last ? 1 : 0) + "&avgOrder=" + (avgOrder ? 0 : 1) + "\">avg</a>" + (avgOrder ? "↓" : ""), "slow", "slow_avg", "max_span",
                    "slow_span", "all_span"));
            for (Entry<CmdMeta, List<String>> e : entry) {
                CmdMeta meta = e.getKey();
                List<String> url = e.getValue();
                long cmdNum = meta.getStat().getAllNum();
                String percentageStr = allNum == 0 || cmdNum == 0 ? "" : NumberStringUtil.DEFAULT_PERCENT.formatByDivide(cmdNum, allNum); // 计算每一个方法的百分比
                String tips = meta.isDisable() ? "Disable" : meta.getTimeout() == 0 ? "NoTimeout" : meta.getTimeout() + "";
                tmp.append(String.format("<tr><td>%-50s</td><td colspan=\"7\">%s</td></tr>\n", meta, printUrlList(url, tips)));
                tmp.append(meta.getStat().toString(timeStatFmt, percentageStr));
                // 增加展示上一次完整10分钟的请求统计数据
                if (lastEntry != null) {
                    for (Entry<CmdMeta, List<String>> e2 : lastEntry) {
                        CmdMeta lastMeta = e2.getKey();
                        if (lastMeta.equals(meta)) {
                            long lastCmdNum = lastMeta.getStat().getAllNum();
                            String lastPercentageStr = lastAllNum == 0 || lastCmdNum == 0 ? "" : "last:" + NumberStringUtil.DEFAULT_PERCENT.formatByDivide(lastCmdNum, lastAllNum);
                            tmp.append(lastMeta.getStat().toString(timeStatFmt, lastPercentageStr));
                            break; // 稍微减少一些不必要的循环,这边时间复杂度为O(n*n)
                        }
                    }
                }
                tmp.append("<tr><td colspan=\"8\">&nbsp;</td></tr>\n");
            }
            tmp.append("</tbody></table></body></html>");
        }
        return tmp.toString();
    }

    /**
     * 显示当前config
     */
    @CmdAdmin(reportToArmero = false)
    public Object config(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return configProcessor.printCurrentConfig(new StringBuilder());
    }

    /**
     * 显示调参的历史记录
     */
    @CmdAdmin(reportToArmero = false)
    public Object configHistory(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder();
        return tmp.append(configProcessor.getResetHistory());
    }

    /**
     * 显示httpServer内部配置
     */
    @CmdAdmin(reportToArmero = false)
    public Object httpServerConfig(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder();
        for (Field f : config.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                tmp.append(String.format("%-24s%-10s\n", f.getName() + ":", f.get(config)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        tmp.append("本地IP:\t\t\t").append(HttpUtil.getLocalIP()).append("\n");
        tmp.append("IP白名单:\t\t").append(IPAuthenticator.getIPAuthenticatorInfo()).append("\n");
        try {
            tmp.append("PIPELINE:\t\t").append(httpServerPipelineFactory.getPipeline()).append("\n");
        } catch (Exception e) {
            log.error("", e);
            tmp.append(e.getMessage()).append("\n");
        }
        return tmp.toString();
    }

    /**
     * 重加载config
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object reloadConfig(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder();
        configProcessor.reloadConfig(tmp);
        return tmp.toString();
    }

    /**
     * 重加载所有命令的设置
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object reloadCmdConfig(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return cmdMappers.resetCmdConfig();
    }

    /**
     * 重加载ipfilter
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object reloadIpfilter(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        boolean localhostpass = request.getParameterBoolean("localpass", IPAuthenticator.LOCALHOST_PASS);
        log.error("START RELOAD IPFILTER,localpass:{}", localhostpass);
        IPAuthenticator.reload(localhostpass);
        return "reset success";
    }

    /**
     * 获得管理员权限激活入口
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object activeIpfilter(XLHttpRequest request, XLHttpResponse response) throws Exception {
        response.setInnerContentType(ContentType.plain);
        int step = request.getParameterInteger("step", -1);
        String key = request.getParameter("key");
        if (step == 1) {
            String mail = request.getParameterCompelled("mail");
            boolean r = IPAuthenticator.sendActiveMail(mail, request.getRemoteIP());
            return "sendActiveMail:" + r;
        } else if (StringTools.isNotEmpty(key)) {
            boolean r = IPAuthenticator.handleActiveMail(request.getParameterCompelled("mail"), request.getRemoteIP(), request.getParameterLong("time"), request.getParameterCompelled("key"));
            return "activeIpfilter:" + r;
        } else {
            Set<String> list = IPAuthenticator.getActiveMailList();
            if (list.isEmpty()) {
                return "ActiveMailList isEmpty";
            }
            response.setInnerContentType(ContentType.html);
            StringBuilder tmp = new StringBuilder();
            tmp.append("<html><body><table><tbody>\n");
            for (String m : list) {
                tmp.append(String.format("<tr><td><a href=\"?step=1&mail=%s\">%s</a></td></tr>\n", m, m));
            }
            tmp.append("</tbody></table></body></html>");
            return tmp.toString();
        }
    }

    /**
     * 重置受临时调参保护的config
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object resetGuardedConfig(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder("reset guarded config...\n");
        configProcessor.resetGuradedConfig(tmp);
        return tmp;
    }

    /**
     * 重置统计
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object resetStat(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        statCmd.reset();
        return "reset stat success";
    }

    /**
     * 临时调参
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object setConfig(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder("set tmp config...\n");
        for (Map.Entry<String, List<String>> e : request.getParameters().entrySet()) {
            String fieldName = e.getKey();
            String value = e.getValue().get(0);
            configProcessor.setFieldValue(fieldName, value, tmp);
        }
        return tmp.toString();
    }

    /**
     * 刷新dns缓存
     */
    @CmdAdmin(reportToArmero = false, type = CmdAdminType.OPER)
    public Object dnsCacheClear(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder("before:\n");
        InetAddressCacheUtil.printCache(tmp);
        InetAddressCacheUtil.cacheClear();
        tmp.append("\n\n--------------------\nnow:\n");
        InetAddressCacheUtil.printCache(tmp);
        return tmp.toString();
    }

    @CmdAdmin(reportToArmero = false)
    public Object dnsCache(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder tmp = new StringBuilder();
        tmp.append("InetAddressCachePolicy.get()         = ").append(InetAddressCachePolicy.get()).append("\n");
        tmp.append("InetAddressCachePolicy.getNegative() = ").append(InetAddressCachePolicy.getNegative()).append("\n");
        tmp.append("\n");
        InetAddressCacheUtil.printCache(tmp);
        return tmp.toString();
    }

    private String printUrlList(List<String> url, String tips) {
        StringBuilder sb = new StringBuilder();
        for (String u : url) {
            sb.append(String.format("<a title=\"%s\" href=\"%s\" target=\"_blank\">%s</a>&nbsp", tips, u, u));
        }
        return sb.toString();
    }

}
