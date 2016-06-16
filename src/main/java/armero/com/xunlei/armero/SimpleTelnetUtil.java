package armero.com.xunlei.armero;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.xunlei.netty.httpserver.component.HeartbeatMessage;
import com.xunlei.util.EmptyChecker;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * @author 曾东
 * @since 2012-10-23 下午3:06:51
 */
public class SimpleTelnetUtil {

    public static final int unknownHostExceptionCode = Integer.MAX_VALUE;
    public static final int ioExceptionCode = Integer.MAX_VALUE / 2;

    /** 受控段上报过来的存在重复的IP，每隔一段时间集中计算一次 */
    public static Set<String> repeatedIps = new HashSet<String>();
    /** 计算间隔的时间 */
    private static int calcRepeatedIpsIntervalTime = 5;

    static {
        ConcurrentUtil.getWatchdog().scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                if (EmptyChecker.isEmpty(MonitorFactory.monitorItemMap)) {
                    return;
                }
                Map<String, Integer> counts = new HashMap<String, Integer>();
                for (MonitorItem item : MonitorFactory.monitorItemMap.values()) {
                    HeartbeatMessage msg = item.getHeartbeatMessage();
                    for (String ip : msg.getIp()) {
                        Integer c = counts.get(ip);
                        if (null != c) {
                            c++;
                        } else {
                            counts.put(ip, 1);
                        }
                    }
                }
                Set<String> repeatedIpsTmp = new HashSet<String>();
                for (Entry<String, Integer> e : counts.entrySet()) {
                    if (e.getValue() > 1) {
                        repeatedIpsTmp.add(e.getKey());
                    }
                }
                repeatedIps = repeatedIpsTmp;
            }
        }, calcRepeatedIpsIntervalTime, calcRepeatedIpsIntervalTime, TimeUnit.SECONDS);
    }

    public static int telnet(String host, int port) {
        long b = System.nanoTime();
        try {
            Socket server = new Socket(host, port);
            long a = System.nanoTime();
            return (int) (a - b);
        } catch (UnknownHostException e) {
            // e.printStackTrace();
            return unknownHostExceptionCode;
        } catch (IOException e) {
            // e.printStackTrace();
            return ioExceptionCode;
        }
    }

    /**
     * 从ip列表中选取 连接最快的ip
     */
    public static String chooseIp(List<String> ipList, int port) {
        if (ipList.size() == 1) {
            return ipList.get(0);
        }
        String select = null;
        // long min = Long.MAX_VALUE;
        long min = ioExceptionCode;
        for (String ip : ipList) {
            int v = telnet(ip, port);
            if (min > v) {
                select = ip;
                min = v;
            }
        }
        return select;
    }

    public static String chooseIp(List<String> ipList, int port, int attempts, int sleepSec) {
        for (int i = 0; i < attempts; i++) {
            String r = chooseIp(ipList, port);
            if (r != null) {
                return r;
            }
            ConcurrentUtil.threadSleep(sleepSec * 1000);
        }
        return null;
    }

    /**
     * 从ip列表中选取 可用的ip，会排除掉重复的
     */
    public static List<String> chooseAvailableIp(List<String> ipList, int port) {
        List<String> l = new ArrayList<String>();
        for (String ip : ipList) {
            if (!repeatedIps.contains(ip)) {
                int v = telnet(ip, port);
                if (v < ioExceptionCode) {
                    l.add(ip);
                }
            }
        }
        return l;
    }
}
