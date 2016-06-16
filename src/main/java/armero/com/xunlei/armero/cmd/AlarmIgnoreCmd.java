package armero.com.xunlei.armero.cmd;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.Log;
import com.xunlei.util.StringTools;
import com.xunlei.util.ValueUtil;

@Service
public final class AlarmIgnoreCmd extends BaseStatCmd {

    private Logger log = Log.getLogger();
    private static final Map<String, Long> ignoreMap = new LinkedHashMap<String, Long>();

    public Object add(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        String name = request.getParameterCompelled("name");
        String time = request.getParameterCompelled("time");

        long t = 0;
        if (StringTools.isNotEmpty(time)) {
            long timeL = ValueUtil.getLong(time, Long.MAX_VALUE);
            if (timeL != Long.MAX_VALUE) { // 给的是数字，表示维持多长时间
                if (timeL <= 0) {
                    t = 0; // 永不过期
                } else {
                    t = System.currentTimeMillis() + timeL * 1000;
                }
            } else if (time.length() == 19) { // 给的是精确到秒的日期格式
                t = DateStringUtil.DEFAULT.parse(time).getTime();
            } else if (time.length() == 10) {// 给的是精确到天的日期格式
                t = DateStringUtil.DEFAULT_DAY.parse(time).getTime();
            } else {
                return "time参数支持格式为数字或19位日期格式或10位日期格式，如time=20,time=2012-12-08 05:12:34,time=2012-12-08";
            }
        }
        ignoreMap.put(name, t);
        String ds = t == 0 ? "N/A" : DateStringUtil.DEFAULT.format(new Date(t));
        log.info("ignoreMap adding:{},expire:{}", new Object[] {
            name,
            ds
        });
        return "忽略列表增加项:" + name + ",过期时间:" + ds;
    }

    public Object remove(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        String name = request.getParameterCompelled("name");
        Long t = ignoreMap.remove(name);
        return t == null ? "忽略列表找不到此项" : "忽略列表删除项:" + name;
    }

    public Object clear(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        ignoreMap.clear();
        return "OK";
    }

    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Long> e : ignoreMap.entrySet()) {
            long t = e.getValue();
            String ds = t == 0 ? "N/A" : DateStringUtil.DEFAULT.format(new Date(t));
            sb.append(e.getKey()).append("\t\t").append(ds).append("\n");
        }
        return sb;
    }

    public static boolean isIgnore(String id) {
        for (Entry<String, Long> e : ignoreMap.entrySet()) {// 低效
            if (id.contains(e.getKey())) {
                long t = e.getValue();
                if (t == 0 || System.currentTimeMillis() < t) {
                    return true;
                }
            }
        }
        return false;
    }

}
