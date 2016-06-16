package com.xunlei.armero;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.common.StatCmd.CoreStat;
import com.xunlei.netty.httpserver.component.HeartbeatMessage;
import com.xunlei.util.HumanReadableUtil;

@Service
public class MonitorFactory {

    // private Logger log = Log.getLogger();

    public static Map<String, MonitorItem> monitorItemMap = Collections.synchronizedMap(new LinkedHashMap<String, MonitorItem>());

    public long login(HeartbeatMessage msg) {
        String globalId = msg.getGlobalId();
        synchronized (globalId.intern()) { // 同步代码块内，需要回访拿到 业务监控清单，时间可能较长，所以需要同步（而且先get，再put也必须同步）
            MonitorItem mi = monitorItemMap.get(globalId);
            if (mi == null) {
                mi = new MonitorItem(msg);
                monitorItemMap.put(globalId, mi);
            } else {
                mi.update(msg);
            }
            return mi.getNewestToken();
        }
    }

    public void logout(String groupId) {
        MonitorItem mi = monitorItemMap.get(groupId);
        if (mi != null) {
            mi.logout();
        }
        monitorItemMap.remove(groupId);
    }

    public static class MonitorCmdFormater {

        public static final String APP = "APP";
        public static final String IP = "IP";
        public static final String HOST = "HOST";
        public static final String PORT = "PORT";

        public static String formatField(String field, String value) {
            StringBuilder sb = new StringBuilder();
            String[] arr = value.split("\\.");
            String v = "";
            for (int i = 0; i < arr.length; i++) {
                String a = arr[i];
                if (i > 0) {
                    v += ".";
                    sb.append(".");
                }
                v += a;
                sb.append(String.format("<a href=\"?field=%s&value=%s\">%s</a>", field, v, a));
            }
            return sb.toString();
        }

        public static String formatField(String field, List<String> value) {
            StringBuilder sb = new StringBuilder();
            for (String v : value) {
                sb.append(formatField(field, v));
                sb.append("&nbsp;");
            }
            return sb.toString();
        }

        public static String list(String filterField, String filterValue) {
            StringBuilder tmp = new StringBuilder();
            tmp.append("<html><body><table width=\"100%\"><tbody>\n");
            tmp.append(String.format(
                    "<tr><td><a href=\"/heartbeat/list\">ALL</a></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                    new Object[] {
                        APP,
                        "REMAIN",
                        HOST,
                        "TPS",
                        "LOAD",
                        "HTTP",
                        "MONITOR",
                        PORT,
                        "PID",
                        "STARTUP",
                        "URL"
                    }));

            CoreStat nullCoreStat = new CoreStat();
            for (MonitorItem item : monitorItemMap.values()) {
                HeartbeatMessage msg = item.getHeartbeatMessage();

                String app = msg.getNameExcludeArgs();
                app = app.replace("com.xunlei.", "");// 去掉常见公共前缀
                if (APP.equals(filterField)) {
                    if (!app.startsWith(filterValue)) {
                        continue;
                    }
                } else if (IP.equals(filterField)) {
                    boolean contain = false;
                    for (String ip : msg.getIp()) {
                        if (ip.startsWith(filterValue)) {
                            contain = true;
                            break;
                        }
                    }
                    if (!contain) {
                        continue;
                    }
                } else if (HOST.equals(filterField)) {
                    if (!msg.getHostName().startsWith(filterValue)) {
                        continue;
                    }
                } else if (PORT.equals(filterField)) {
                    if (!(msg.getPort() + "").equals(filterValue)) {
                        continue;
                    }
                }

                CoreStat coreStat = msg.getCoreStat();
                if (coreStat == null) {
                    coreStat = nullCoreStat;
                }
                String tps = coreStat.getTps() == -1 ? "N/A" : coreStat.getTps() + "";
                String load = coreStat.getLoad() < 0 ? "N/A" : coreStat.getLoad() + "";
                String http = HumanReadableUtil.timeSpan(coreStat.getSpan()) + " " + coreStat.getConn();
                tmp.append(String
                        .format("<tr><td><a href=\"/heartbeat/logout?id=%s\">X</a></td><td %s>%s</td><td>%s</td><td>%s</td><td>%s</td><td %s>%s</td><td>%s</td> <td %s>%s</td><td>%s</td><td>%s</td><td>%s</td>",
                                new Object[] {
                                    msg.getGlobalId(),
                                    msg.isWarn() ? "style=\"background-color: #DBE5F1;\"" : "",
                                    formatField(APP, app),
                                    HumanReadableUtil.timeSpan(msg.getRemainTime()),
                                    // formatField(HOST, msg.getHostName()), formatField(IP, msg.getIp()), formatField(PORT, msg.getPort() + ""), Integer.valueOf(msg.getPid()),
                                    // msg.getStartupTimeStr() }));
                                    formatField(HOST, msg.getHostName()),
                                    tps,
                                    coreStat.isLoadHigh() ? "style=\"background-color: #CD5C5C;\"" : "",
                                    load,
                                    http,
                                    Boolean.FALSE == item.getLastMonitorResult() ? "style=\"background-color: #E8E8D0;\"" : "",
                                    item.getLastMonitorInfo().replaceAll(" ", "&nbsp;"),
                                    formatField(PORT, msg.getPort() + ""),
                                    Integer.valueOf(msg.getPid()),
                                    msg.getStartupTimeStr()
                                }));
                tmp.append("<td>");
                // List<String> urls = msg.getLogicUrls();
                // List<String> absoluteUrls = msg.getLogicAbsoluteUrls();
                // for (int i = 0; i < urls.size(); i++) {
                // String absoluteUrl = absoluteUrls.get(i);
                // String url = urls.get(i);
                tmp.append(String.format("<a href=\"%s\" target=\"_blank\">%s</a>&nbsp;", new Object[] {
                    msg.getRootUrl(),
                    "/"
                }));
                // }
                tmp.append("</td></tr>");
            }
            tmp.append("</tbody></table></body></html>");
            return tmp.toString();
        }
    }
}
