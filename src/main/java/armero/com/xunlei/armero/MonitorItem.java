package armero.com.xunlei.armero;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import com.xunlei.armero.cmd.AlarmIgnoreCmd;
import com.xunlei.json.JSONEngine;
import com.xunlei.json.JSONUtil;
import com.xunlei.netty.httpserver.cmd.annotation.CmdMonitor;
import com.xunlei.netty.httpserver.cmd.common.ArmeroReportCmd.CmdManifest;
import com.xunlei.netty.httpserver.cmd.common.ArmeroReportCmd.CmdMonitorItem;
import com.xunlei.netty.httpserver.component.HeartbeatMessage;
import com.xunlei.proxy.HttpClientUtil;
import com.xunlei.proxy.HttpClientUtil.HttpResponseHandler;
import com.xunlei.proxy.SimpleHttpClient;
import com.xunlei.util.CharsetTools;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.EmptyChecker;
import com.xunlei.util.HumanReadableUtil;
import com.xunlei.util.Log;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * @author 曾东
 * @since 2012-10-11 下午3:51:31
 */
public class MonitorItem implements Runnable {

    private final Logger log_detail;
    private final Logger log_onoff;
    private final Logger log_alarm;
    private final Logger log_alarm_ignore;

    private HeartbeatMessage heartbeatMessage; // 心跳信息
    private ScheduledFuture<?> scheduledFuture; // 定时监控器
    private MonitorItemManifest manifest; // 业务监控清单
    private List<String> ip; // 可连接ip列表
    private String lastMonitorTime = "";
    private Boolean lastMonitorResult;
    private String lastMonitorSimpleInfo = "";
    /** 生成ruiz与armero的信任token */
    private long token = System.nanoTime();// nanotime在此场景够随机了

    // 给ruiz login时返回最新的验证token
    public long getNewestToken() {
        long newToken = System.nanoTime();
        if ((newToken - token) / 1000000000 > ArmeroUtil.getTokenExpireSec()) {
            token = newToken;
        }
        return token;
    }

    public String getLastMonitorInfo() {
        return String.format("%-8s(%-3s) %-19s %s", heartbeatMessage.getGlobalCode(), manifest == null ? "N/A" : manifest.getLength(), lastMonitorTime, lastMonitorSimpleInfo);
    }

    public Boolean getLastMonitorResult() {
        return lastMonitorResult;
    }

    public MonitorItem(HeartbeatMessage heartbeatMessage) {
        this.heartbeatMessage = heartbeatMessage;
        String id = heartbeatMessage.getGlobalId();
        log_detail = Log.getLogger("DETAIL." + id); // 获取清单，及清单具体监控详情
        log_onoff = Log.getLogger("ONOFF." + id);// 上下线
        log_alarm = Log.getLogger("ALARM." + id);// 邮件报警专用
        log_alarm_ignore = Log.getLogger("ALARM_IGNORE." + id);// 邮件报警专用

        List<String> ipList = SimpleTelnetUtil.chooseAvailableIp(heartbeatMessage.getIp(), heartbeatMessage.getPort()); // 过滤出可用的ip
        if (EmptyChecker.isEmpty(ipList)) {
            this.ip = heartbeatMessage.getIp();
            log_onoff.warn("app available_ip null,msg:{}", new Object[] {
                heartbeatMessage
            });
        } else {
            this.ip = ipList;
            log_onoff.debug("app available_ip:{},msg:{}", new Object[] {
                ipList,
                heartbeatMessage
            });
        }

        resetScheduledFuture(true);
    }

    private static final AtomicInteger scheduleMonitorStepCounter = new AtomicInteger();// 让每个定时任务不要挤在一起
    private static final int scheduleMonitorStepSec = 5;// 每个定时任务步进5s

    private ScheduledFuture<?> resetScheduledFuture(boolean login) {
        int oriSec = -1;
        if (scheduledFuture != null) {
            oriSec = (int) scheduledFuture.getDelay(TimeUnit.SECONDS);
            scheduledFuture.cancel(true);
        }
        this.scheduledFuture = null;
        int delay = heartbeatMessage.getHeartbeatMonitorSec();

        String info = "";
        if (delay > 0) {
            int initial = (scheduleMonitorStepCounter.incrementAndGet() * scheduleMonitorStepSec) % delay;
            info = "monitor per " + delay + " sec,from " + DateStringUtil.DEFAULT.format(new Date(System.currentTimeMillis() + initial * 1000)) + "[" + initial + "]";
            ScheduledFuture<?> newScheduledFuture = ConcurrentUtil.getDaemonExecutor().scheduleAtFixedRate(this, initial, delay, TimeUnit.SECONDS);
            this.scheduledFuture = newScheduledFuture;
            if (oriSec > 0) {
                info = info + ",ori per " + oriSec + " sec";
            }
        }
        log_onoff.warn("app {},{},msg:{}", new Object[] {
            login ? "login" : "update",
            info,
            heartbeatMessage
        });
        return scheduledFuture;
    }

    /**
     * <pre>
     * 更新 心跳信息：
     * 1.如果监控间隔有变，重新监控
     * 2.如果Pid有变，说明app有重启
     */
    public void update(HeartbeatMessage nowMsg) {
        HeartbeatMessage oriMsg = heartbeatMessage;

        int oriPid = oriMsg.getPid();
        int nowPid = nowMsg.getPid();

        int oriSec = heartbeatMessage.getHeartbeatMonitorSec();
        int nowSec = nowMsg.getHeartbeatMonitorSec();

        this.heartbeatMessage = nowMsg;

        StringBuilder changeInfo = new StringBuilder();
        if (nowSec != oriSec) { // 1.如果监控间隔有变，重新监控
            changeInfo.append(",heartbeatMonitorSec:").append(oriSec).append("->").append(nowSec);
            resetScheduledFuture(false);
        }
        if (nowPid != oriPid) {// 2.如果Pid有变，说明app有重启
            log_onoff.warn("app update,pid:{}->{},msg:{}", new Object[] {
                oriPid,
                nowPid,
                heartbeatMessage
            }); // 更新状态
            manifest = null; // 业务监控清单需要更新
        }
    }

    /**
     * 获取业务监控清单，如果没有清单，会向app请求最新的一份清单
     */
    public synchronized MonitorItemManifest getManifest(String ip) {
        if (this.manifest == null) {
            String url = heartbeatMessage.getManifestUrl(ip);
            String resp = HttpClientUtil.httpGet(url, ArmeroUtil.getHttpAttempts(), ArmeroUtil.getHttpRetrySleepSec());
            if (resp == null) {// 连接不上服务器，下次再重试
                return null;
            }
            try {
                CmdManifest cm = JSONEngine.DEFAULT_JACKSON_MAPPER.readValue(resp, CmdManifest.class);
                log_onoff.info("app minifest:{}\n{}", new Object[] {
                    heartbeatMessage,
                    JSONUtil.fromObjectPretty(cm)
                });
                MonitorItemManifest mim = new MonitorItemManifest(cm);
                this.manifest = mim;
            } catch (Exception e) {// 解析失败，当作 无清单
                log_onoff.error("parse error:{}", resp, e);
                this.manifest = new MonitorItemManifest(null);
            }
        }
        return this.manifest;
    }

    /**
     * 退出监控
     */
    public void logout() {
        log_onoff.warn("app logout,msg:{}", new Object[] {
            heartbeatMessage
        });// 退出
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    public HeartbeatMessage getHeartbeatMessage() {
        return heartbeatMessage;
    }

    @Override
    public void run() {// 业务监控具体逻辑
        String id = heartbeatMessage.getGlobalId();
        log_detail.debug("MONITOR BEGIN,{}", new Object[] {
            id
        });
        Boolean result = false;

        try {
            final int attempts = ArmeroUtil.getHttpAttempts();
            final int sleepSec = ArmeroUtil.getHttpRetrySleepSec();
            String choosedIp = SimpleTelnetUtil.chooseIp(ip, heartbeatMessage.getPort(), attempts, sleepSec);
            if (choosedIp == null) {// 说明根本telnet不了
                lastMonitorSimpleInfo = "CANT TELNET";
                log_alarm.error("MONITOR CANT TELNET,ip:{},heartbeat:{}\t{}", new Object[] {
                    ip,
                    HumanReadableUtil.timeSpan(heartbeatMessage.getRemainTime()),
                    id
                });
                return;
            }

            log_detail.debug("MONITOR CHOOSE IP:{} from {},{}", new Object[] {
                choosedIp,
                ip,
                id
            });
            String echoUrl = heartbeatMessage.getEchoUrl(choosedIp);
            int echoCode = HttpClientUtil.httpHead(echoUrl, attempts, sleepSec);
            if (echoCode != HttpStatus.SC_OK) {// 说明服务器ping不上，报警
                lastMonitorSimpleInfo = "CANT ECHO";
                log_alarm.error("MONITOR CANT ECHO,url:{},code:{},heartbeat:{}\t{}", new Object[] {
                    echoUrl,
                    echoCode,
                    HumanReadableUtil.timeSpan(heartbeatMessage.getRemainTime()),
                    id
                });
                return;
            }

            long before = System.currentTimeMillis();
            MonitorItemManifest manifest = getManifest(choosedIp);
            if (manifest == null) {
                lastMonitorSimpleInfo = "CANT GET MANIFEST";
                log_detail.error("MONITOR CANT GET MANIFEST,{}", new Object[] {
                    id
                });
                return;
            }

            List<CmdMonitorItem> cmis = manifest.getMonitorItem();
            String rootUrl = heartbeatMessage.getRootUrl(choosedIp);
            List<String> failInfoList = new ArrayList<String>();

            int cmisLen = manifest.getLength();
            if (EmptyChecker.isNotEmpty(cmis)) {
                for (final CmdMonitorItem cmi : cmis) {
                    String userIdParam = cmi.getUserId() > 0 ? "&" + HeartbeatMessage.PARAM_USERID + "=" + cmi.getUserId() : "";
                    final String url = rootUrl + cmi.getPath() + "?" + HeartbeatMessage.PARAM_TOKEN + "=" + token + userIdParam + "&" + cmi.getParam();
                    final StringBuilder itemInfo = new StringBuilder();
                    final AtomicInteger trys = new AtomicInteger();
                    HttpResponseHandler h = new HttpResponseHandler() {

                        @Override
                        public void handle(HttpResponse resp) throws Throwable {
                            int status = resp.getStatusLine().getStatusCode();
                            long len = resp.getEntity().getContentLength();

                            String content = EntityUtils.toString(resp.getEntity(), CharsetTools.UTF_8.name());

                            int wantStatus = cmi.getStatus();
                            int lenMin = cmi.getLengthMin();
                            int wantRtn = cmi.getRtn();
                            String[] contains = cmi.getContains();
                            String location = null;
                            boolean redirect = status >= 300 && status < 400;
                            String rtnContains = redirect ? "%22rtn%22:" + wantRtn : "\"rtn\":" + wantRtn;
                            if (redirect) {// 重定向
                                Header locationHeader = resp.getFirstHeader(HttpHeaders.Names.LOCATION);
                                location = locationHeader.getValue();
                                content = content + "\n\n" + location;
                            }

                            if (wantStatus > 0 && status != wantStatus) {
                                itemInfo.append("STATUS UNMATCH actual:").append(status).append(",want:").append(wantStatus);
                            } else if (lenMin > 0 && lenMin > len) {
                                itemInfo.append("LENGTH TOO SHORT ").append(len).append("<").append(lenMin);
                            } else if (wantRtn > -1 && !content.contains(rtnContains)) {
                                itemInfo.append("RTN UNMATCH want:").append(wantRtn);
                            } else {
                                if (EmptyChecker.isNotEmpty(contains)) {
                                    for (String c : contains) {
                                        if (!content.contains(c)) {
                                            itemInfo.append("CONTENT NOT CONTAINS KEY WORD:").append(c);
                                            break;
                                        }
                                    }
                                }
                            }
                            if (itemInfo.length() > 0) { // 有错误时，打印详细
                                itemInfo.append("\nURL:").append(url);
                                itemInfo.append("\nSTATUS:").append(status);
                                itemInfo.append("\tLENGTH:").append(len);
                                itemInfo.append("\tTIME:").append(DateStringUtil.DEFAULT.now());
                                itemInfo.append("\n").append(content);
                            }
                            trys.set(attempts);// http请求没有问题时，不重试
                        }

                        @Override
                        public void exceptionCaught(HttpRequestBase req, Throwable ex) {
                            if (trys.get() < attempts) {// http请求有问题时，暂停然后重试
                                ConcurrentUtil.threadSleep(sleepSec);
                            } else {
                                itemInfo.append("FAIL TO RESPONSE");
                                itemInfo.append("\nURL:").append(url);
                                itemInfo.append("\nTIME:").append(DateStringUtil.DEFAULT.now());
                            }
                        }
                    };
                    int so = cmi.getSoTimeout();
                    int conn = cmi.getConnTimeout();
                    boolean useDefault = so == CmdMonitor.DEFAULT_TIMEOUT && conn == CmdMonitor.DEFAULT_TIMEOUT;// 拿指定超时的httpClient来进行连接
                    HttpClient hc = useDefault ? SimpleHttpClient.default_http_client : SimpleHttpClient.getInstance(cmi.getSoTimeout(), cmi.getConnTimeout());
                    while (trys.getAndIncrement() < attempts) {
                        h.httpGet(hc, url);
                    }

                    boolean r = itemInfo.length() == 0;
                    if (r) {
                        log_detail.debug("TEST OK,{},url:{}", new Object[] {
                            id,
                            url
                        });
                    } else {
                        failInfoList.add(itemInfo.toString());
                        log_detail.error("TEST FAIL,{},{}", new Object[] {
                            id,
                            itemInfo
                        });
                    }
                }
            }

            long t = System.currentTimeMillis() - before;
            String time = t > 0 ? HumanReadableUtil.timeSpan(System.currentTimeMillis() - before) : "0MS";
            if (failInfoList.isEmpty()) {
                result = true;
                lastMonitorSimpleInfo = "MONITOR OK";
                log_alarm.debug("MONITOR OK({}),{} USING {}", new Object[] {
                    cmisLen,
                    id,
                    time
                });
            } else {
                lastMonitorSimpleInfo = "MONITOR FAIL";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < failInfoList.size(); i++) {
                    sb.append(i + 1).append("\t").append(failInfoList.get(i)).append("\n");
                }
                Logger al = AlarmIgnoreCmd.isIgnore(id) ? log_alarm_ignore : log_alarm;
                String noAlarm = al == log_alarm ? String.format("\n不再报警：%s/alarmIgnore/add?name=%s&time=%s ", ArmeroUtil.getRootUrl(), id,
                        DateStringUtil.DEFAULT.format(new Date(System.currentTimeMillis() + 24 * 3600 * 1000))) : "";
                al.error("MONITOR FAIL({}/{}),{} USING {}\n{}{}", new Object[] {
                    failInfoList.size(),
                    cmisLen,
                    id,
                    time,
                    sb,
                    noAlarm
                });
            }
        } catch (Throwable e) {
            lastMonitorSimpleInfo = "MONITOR EXCEPTION";
            log_alarm.error("MONITOR EXCEPTION,{}", new Object[] {
                id,
                e
            });
        } finally {
            lastMonitorResult = result;
            lastMonitorTime = DateStringUtil.DEFAULT.now();
        }
    }
}
