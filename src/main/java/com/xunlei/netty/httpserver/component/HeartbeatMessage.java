package com.xunlei.netty.httpserver.component;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import com.xunlei.netty.httpserver.cmd.common.StatCmd.CoreStat;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.SystemInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatMessage {

    public static final String PARAM_TOKEN = "armeroToken";
    public static final String PARAM_USERID = "armeroUserId";

    /** 程序启动名 */
    private String name;
    /** 程序所处机器IP */
    private List<String> ip;
    /** 程序所处机器hostName */
    private String hostName;
    /** 程序PID */
    private int pid;
    /** ruiz程序监听http端口号 */
    private int port;
    /** 程序上报监控中心的心跳间隔，发送了reportMsg如果超过了heartbeatSec，说明此程序没有了心跳 */
    private int heartbeatAlarmSec;
    /** Armero回访监控此app间隔 */
    private int heartbeatMonitorSec = 10 * 60;
    /** 程序启动时间 */
    private long startupTime;
    /** 给程序打的标签，用于监控中心进行分类过滤显示 */
    private List<String> tags;

    private CoreStat coreStat;
    @JsonIgnore
    private String remoteIp; // 监控中心回调用，把 远程ip记录下来
    @JsonIgnore
    private String startupTimeStr;
    @JsonIgnore
    private long reportTime = System.currentTimeMillis(); // 这个字段是 监控中心收到包后用的
    @JsonIgnore
    private String globalId;

    public HeartbeatMessage() {
    }

    public HeartbeatMessage(int port, List<String> ip, int heartbeatAlarmSec, int heartbeatMonitorSec, List<String> tags) {
        this.port = port;
        this.ip = ip;
        this.heartbeatAlarmSec = heartbeatAlarmSec;
        this.heartbeatMonitorSec = heartbeatMonitorSec;
        this.name = SystemInfo.COMMAND_FULL;
        this.hostName = SystemInfo.HOSTNAME;
        this.pid = SystemInfo.PID;
        this.startupTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.tags = tags;
    }

    public String getGlobalId() {
        if (this.globalId == null) {
            this.globalId = getHostName() + "." + SystemInfo.getCommand(this.name, true) + ":" + port + ":" + getGlobalCode();
        }
        return this.globalId;
    }

    public String getGlobalCode() {
        String gid = (getNameExcludeArgs() + ":" + this.ip + ":" + this.port);
        return Integer.toHexString(gid.hashCode());
    }

    public int getHeartbeatAlarmSec() {// 设置错的话，默认是一小时
        if (heartbeatAlarmSec <= 0) {
            heartbeatAlarmSec = 3600;
        }
        return this.heartbeatAlarmSec;
    }

    public List<String> getIp() {
        return this.ip;
    }

    public String getName() {
        return this.name;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPid() {
        return this.pid;
    }

    public int getPort() {
        return this.port;
    }

    @JsonIgnore
    public long getRemainTime() {
        return this.reportTime + this.heartbeatAlarmSec * 1000 - System.currentTimeMillis();
    }

    public String getRemoteIp() {
        return this.remoteIp;
    }

    public long getReportTime() {
        return this.reportTime;
    }

    public long getStartupTime() {
        return this.startupTime;
    }

    @JsonIgnore
    public String getStartupTimeStr() {
        if (this.startupTimeStr == null) {
            this.startupTimeStr = DateStringUtil.DEFAULT.format(new Date(this.startupTime));
        }
        return this.startupTimeStr;
    }

    public void setCoreStat(CoreStat coreStat) {
        this.coreStat = coreStat;
    }

    @JsonIgnore
    public boolean isWarn() {
        return getRemainTime() < 0L;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getHeartbeatMonitorSec() {
        return heartbeatMonitorSec;
    }

    /**
     * 获取 业务监控manifest-url 用于定时更新监控清单
     */
    public String getManifestUrl(String ip) {
        return getRootUrl(ip) + "/armeroReport/manifest";
    }

    /**
     * 获取 业务监控ping-url 用于在业务监控前，监控是否整个app无法响应
     */
    public String getEchoUrl(String ip) {
        return getRootUrl(ip) + "/echo/now";
    }

    public String getRootUrl(String ip) {
        return "http://" + ip + ":" + this.port;
    }

    @JsonIgnore
    public String getRootUrl() {
        return "http://" + getRemoteIp() + ":" + this.port;
    }

    public CoreStat getCoreStat() {
        return coreStat;
    }

    @JsonIgnore
    public String getNameExcludeArgs() {
        return SystemInfo.getCommand(this.name, false);
    }

    @Override
    public String toString() {
        return String.format("HeartbeatMessage %s[name=%s, hostName=%s, ip=%s, pid=%s, port=%s, heartbeatAlarmSec=%s, heartbeatMonitorSec=%s, startupTime=%s,  reportTime=%s]", getGlobalId(), name,
                hostName, ip, pid, port, heartbeatAlarmSec, heartbeatMonitorSec, startupTime, reportTime);
    }
}
