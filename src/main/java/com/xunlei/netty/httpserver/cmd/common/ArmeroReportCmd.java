package com.xunlei.netty.httpserver.cmd.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.json.JSONUtil;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.CmdMappers;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.cmd.annotation.Cmd;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin.CmdAdminType;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAuthor;
import com.xunlei.netty.httpserver.cmd.annotation.CmdContentType;
import com.xunlei.netty.httpserver.cmd.annotation.CmdMonitor;
import com.xunlei.netty.httpserver.cmd.annotation.CmdMonitors;
import com.xunlei.netty.httpserver.cmd.annotation.CmdSession;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.util.HttpUtil;
import com.xunlei.util.StringTools;
import com.xunlei.util.SystemInfo;

/**
 * 上报给armero的接口
 * 
 * @author 曾东
 * @since 2012-12-7 下午6:14:49
 */
@Service
public class ArmeroReportCmd extends BaseStatCmd {

    // private static final Logger log = Log.getLogger();
    @Autowired
    private CmdMappers cmdMappers;

    public static boolean isIpSegValid(String ipSeg) {// 简单判断，并不是很严格判断，如12341234.12 这种也认为是ip片断，问题不大
        boolean isIpSeg = true;
        boolean containDot = false;
        for (int i = 0; i < ipSeg.length(); i++) {
            char c = ipSeg.charAt(i);
            if (c == '.') {
                containDot = true;
                continue;
            }
            if (c < '0' || c > '9') {
                isIpSeg = false;
                break;
            }
        }
        return containDot && isIpSeg;
    }

    private boolean isCmdMonitorEnable(CmdMonitor cm) {
        if (StringTools.isEmpty(cm.enableOn())) { // 没有设置，说明可用
            return true;
        }
        if (SystemInfo.HOSTNAME.contains(cm.enableOn())) {
            return true;
        }
        if (isIpSegValid(cm.enableOn())) {
            for (String ip : HttpUtil.getLocalIP()) {
                if (ip.contains(cm.enableOn())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CmdMonitorItem> monitorItemList;
    private List<CmdAdminItem> adminItemList;

    public List<CmdAdminItem> getAdminItemList() {
        if (adminItemList == null) {
            List<CmdAdminItem> adminItemListTmp = new ArrayList<CmdAdminItem>();
            Map<CmdMeta, List<String>> cmd_urls_map = cmdMappers.getReverseCmdAllSortedMap();
            for (Entry<CmdMeta, List<String>> e : cmd_urls_map.entrySet()) {
                CmdMeta meta = e.getKey();
                if (!meta.isDisable()) {
                    CmdAdmin cm = meta.getMethod().getAnnotation(CmdAdmin.class);
                    if (cm != null) {
                        if (cm.reportToArmero()) {
                            adminItemListTmp.add(new CmdAdminItem(meta, cm, e.getValue()));
                        }
                    }
                }
            }
            adminItemList = adminItemListTmp;
        }
        return adminItemList;
    }

    public List<CmdMonitorItem> getMonitorItemList() {
        if (monitorItemList == null) {
            Map<CmdMeta, List<String>> cmd_urls_map = cmdMappers.getReverseCmdAllSortedMap();
            List<CmdMonitorItem> items = new ArrayList<CmdMonitorItem>();
            for (Entry<CmdMeta, List<String>> e : cmd_urls_map.entrySet()) {
                CmdMeta meta = e.getKey();
                if (!meta.isDisable()) {
                    CmdMonitor cm = meta.getMethod().getAnnotation(CmdMonitor.class);
                    if (cm != null) {
                        if (isCmdMonitorEnable(cm)) {
                            items.add(new CmdMonitorItem(meta, cm, e.getValue()));
                        }
                    }
                    CmdMonitors cms = meta.getMethod().getAnnotation(CmdMonitors.class);
                    if (cms != null) {
                        for (CmdMonitor c : cms.value()) {
                            if (isCmdMonitorEnable(c)) {
                                items.add(new CmdMonitorItem(meta, c, e.getValue()));
                            }
                        }
                    }
                }
            }
            monitorItemList = items;
        }
        return monitorItemList;
    }

    public static class CmdManifest {

        private List<CmdMonitorItem> monitorItem;
        private List<CmdAdminItem> adminItem;

        public CmdManifest() {
        }

        public CmdManifest(List<CmdMonitorItem> monitorItem, List<CmdAdminItem> adminItem) {
            this.monitorItem = monitorItem;
            this.adminItem = adminItem;
        }

        public List<CmdMonitorItem> getMonitorItem() {
            return monitorItem;
        }

        public List<CmdAdminItem> getAdminItem() {
            return adminItem;
        }
    }

    public static class CmdAdminItem {

        private String path;
        private CmdAdminType type;

        public CmdAdminItem() {// 用于json readValue
        }

        public CmdAdminItem(CmdMeta meta, CmdAdmin ca, List<String> urls) {
            this.path = urls.get(0);
            this.type = ca.type();
        }

        public String getPath() {
            return path;
        }

        public CmdAdminType getType() {
            return type;
        }

        @Override
        public String toString() {
            return String.format("CmdAdminItem [path=%s, type=%s]", path, type);
        }
    }

    public static class CmdMonitorItem {

        private String name;
        private String[] author;
        private String path;
        private String param;
        private int status;
        private int rtn;
        private String[] contains;
        private int lengthMin;
        private int connTimeout;
        private int soTimeout;
        private int userId;

        public CmdMonitorItem() {// 用于json readValue
        }

        public CmdMonitorItem(CmdMeta meta, CmdMonitor cmdMonitor, List<String> urls) {
            Cmd cmd = meta.getMethod().getAnnotation(Cmd.class);
            CmdAuthor cmdAuthor = meta.getMethod().getAnnotation(CmdAuthor.class);
            CmdContentType cmdContentType = meta.getMethod().getAnnotation(CmdContentType.class);
            CmdSession cmdSession = meta.getMethod().getAnnotation(CmdSession.class);
            // CmdParams params = meta.getMethod().getAnnotation(CmdParams.class);
            // CmdReturn ret = meta.getMethod().getAnnotation(CmdReturn.class);
            if (cmd != null) {
                this.name = cmd.value();
            }
            if (cmdAuthor != null) {
                this.author = cmdAuthor.value();
            }
            this.path = urls.get(0);

            this.param = cmdMonitor.param();
            this.status = cmdMonitor.status();
            this.contains = cmdMonitor.contains();
            this.rtn = cmdMonitor.rtn();
            this.lengthMin = cmdMonitor.lengthMin();
            this.connTimeout = cmdMonitor.connTimeout();
            this.soTimeout = cmdMonitor.soTimeout();
            this.userId = cmdMonitor.userId();
        }

        public String getName() {
            return name;
        }

        public int getConnTimeout() {
            return connTimeout;
        }

        public int getSoTimeout() {
            return soTimeout;
        }

        public String[] getAuthor() {
            return author;
        }

        public String getPath() {
            return path;
        }

        public String getParam() {
            return param;
        }

        public int getStatus() {
            return status;
        }

        public int getRtn() {
            return rtn;
        }

        public String[] getContains() {
            return contains;
        }

        public int getLengthMin() {
            return lengthMin;
        }

        public int getUserId() {
            return userId;
        }

        @Override
        public String toString() {
            return String.format("CmdMonitorItem [name=%s, author=%s, path=%s, param=%s, status=%s, rtn=%s, contains=%s, lengthMin=%s, connTimeout=%s, soTimeout=%s, userId=%s]", name,
                    Arrays.toString(author), path, param, status, rtn, Arrays.toString(contains), lengthMin, connTimeout, soTimeout, userId);
        }
    }

    @CmdAdmin(reportToArmero = false)
    public Object manifest(XLHttpRequest request, XLHttpResponse response) throws Exception {
        // init(request, response); // 不加验证
        response.setInnerContentType(ContentType.plain);
        // boolean pretty = request.getParameterBoolean("pretty", true);
        boolean pretty = true;
        List<CmdMonitorItem> monitorItem = getMonitorItemList();
        List<CmdAdminItem> adminItem = getAdminItemList();
        CmdManifest cm = new CmdManifest(monitorItem, adminItem);
        return pretty ? JSONUtil.fromObjectPretty(cm) : JSONUtil.fromObject(cm);
    }

}
