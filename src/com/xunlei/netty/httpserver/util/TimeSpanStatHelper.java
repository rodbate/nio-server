package com.xunlei.netty.httpserver.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.a2a.googlechart.ChartAxis;
import com.a2a.googlechart.charts.LineChart;
import com.a2a.googlechart.data.ChartData;
import com.a2a.googlechart.data.VectorInt;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.cmd.CmdMappers;
import com.xunlei.netty.httpserver.cmd.CmdMappers.CmdMeta;
import com.xunlei.netty.httpserver.cmd.CmdMappers.StageTimeSpanStat;
import com.xunlei.netty.httpserver.cmd.common.StatCmd;
import com.xunlei.netty.httpserver.cmd.common.StatCmd.StreamStat;
import com.xunlei.netty.httpserver.util.TimeSpanStatHelper.OtherStatSbapshotQueue.OtherTimeSpanStatResult;
import com.xunlei.netty.httpserver.util.TimeSpanStatHelper.TimeSpanStatSbapshotQueue.TimeSpanStatResult;
import com.xunlei.spring.AfterBootstrap;
import com.xunlei.spring.AfterConfig;
import com.xunlei.spring.Config;
import com.xunlei.util.ConcurrentCircularQueue;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.HumanReadableUtil;
import com.xunlei.util.Log;
import com.xunlei.util.NumberStringUtil;
import com.xunlei.util.SystemMonitor;
import com.xunlei.util.concurrent.BaseSchedulable;
import com.xunlei.util.stat.TimeSpanStat;

/**
 * TimeSpanStatHelper统计监控帮助类
 */
@Service
public class TimeSpanStatHelper {

    private Map<TimeSpanStat, TimeSpanStatSbapshotQueue> queueMap = new LinkedHashMap<TimeSpanStat, TimeSpanStatSbapshotQueue>();

    @Config(resetable = true)
    private long timeSpanStatSnapshotSec = 10 * 60; // 每十分钟统计一次

    @Config(resetable = true)
    private int timeSpanStatQueueSize = 24 * 6; // 默认保存一天的记录 （10分钟 * 6 * 24 刚好是一天的跨度）

    @Autowired
    private StatCmd statCmd;
    @Autowired
    private CmdMappers cmdMappers;
    private OtherStatSbapshotQueue otherStatSbapshotQueue;
    public static final Logger timespanStatLog = Log.getLoggerWithPrefix("timespanStat");
    private SettingCmdsTimeSpanSbapshot settingCmdsTSS = new SettingCmdsTimeSpanSbapshot();

    public SettingCmdsTimeSpanSbapshot getSettingCmdsTSS() {
        return settingCmdsTSS;
    }

    /**
     * <pre>
     * 定时任务每隔timeSpanStatSnapshotSec快照一次
     * 1.http请求量统计快照
     * 2.setting/cmds请求量快照
     * 3.stream流量统计快照
     * </pre>
     */
    private BaseSchedulable sche = new BaseSchedulable() {

        @Override
        public void process() throws Throwable {
            for (TimeSpanStatSbapshotQueue q : queueMap.values()) {
                q.snapshot(); // 当前timespan统计队列里就一个元素
            }
            settingCmdsTSS.snapshot();
            otherStatSbapshotQueue.snapshot();
        }

    };

    /**
     * cmd请求量统计快照类
     */
    public class SettingCmdsTimeSpanSbapshot {

        private Map<CmdMeta, List<String>> lastCmdUrlsMap = new LinkedHashMap<CmdMeta, List<String>>();
        private Map<CmdMeta, List<String>> last10minCmdUrlsMap = new LinkedHashMap<CmdMeta, List<String>>(); // 保存住上一个完整10分钟内的统计量

        public Map<CmdMeta, List<String>> getLast10minCmdUrlsMap() {
            return last10minCmdUrlsMap;
        }

        /**
         * 10分钟进行一次快照,当前快照-上一次快照=上一个完整10分钟请求量统计
         */
        public void snapshot() {
            Map<CmdMeta, List<String>> nowCmdUrlsMap = cmdMappers.getReverseCmdAllSortedMap(); // 当前请求量统计
            Map<CmdMeta, List<String>> newCmdUrlsMap = new LinkedHashMap<CmdMeta, List<String>>(); // 当前快照的请求量统计
            Collection<Map.Entry<CmdMeta, List<String>>> tmpEntry = nowCmdUrlsMap.entrySet();
            for (Entry<CmdMeta, List<String>> e : tmpEntry) {
                CmdMeta nowcm = e.getKey();
                List<String> url = e.getValue();
                BaseCmd nowcmd = nowcm.getCmd();
                Method method = nowcm.getMethod();
                CmdMeta meta = new CmdMeta(nowcmd, method);
                StageTimeSpanStat stat = new StageTimeSpanStat("settingcmds");
                stat.setAllNum(nowcm.getStat().getAllNum());
                stat.setAllSpan(nowcm.getStat().getAllSpan());
                stat.setSlowNum(nowcm.getStat().getSlowNum());
                stat.setSlowSpan(nowcm.getStat().getSlowSpan());
                stat.setMaxSpan(nowcm.getStat().getMaxSpan());
                nowcm.getStat().setMaxSpan(0l);
                meta.setStat(stat);
                newCmdUrlsMap.put(meta, url);
            }
            if (lastCmdUrlsMap != null && lastCmdUrlsMap.size() > 0) { // 过滤掉第一次情况
                last10minCmdUrlsMap = getIntervalCmdUrlsMap(newCmdUrlsMap, lastCmdUrlsMap);
            }
            lastCmdUrlsMap = newCmdUrlsMap;
        }

        /**
         * 将公共方法提取出来处理,构造一个新的CmdMeta Map对象返回
         */
        protected Map<CmdMeta, List<String>> getIntervalCmdUrlsMap(Map<CmdMeta, List<String>> end, Map<CmdMeta, List<String>> begin) {
            Map<CmdMeta, List<String>> intervalCmdUrlsMap = new LinkedHashMap<CmdMeta, List<String>>();
            if (null == end || end.size() == 0) {
                return intervalCmdUrlsMap;
            }
            if (null == begin || begin.size() == 0) {
                return end;
            }
            Collection<Map.Entry<CmdMeta, List<String>>> nowEntry = end.entrySet();
            Collection<Map.Entry<CmdMeta, List<String>>> lastEntry = begin.entrySet();
            for (Entry<CmdMeta, List<String>> now : nowEntry) { // O(n*n)时间复杂度
                CmdMeta nowcm = now.getKey();
                List<String> url = now.getValue();
                BaseCmd nowcmd = nowcm.getCmd();
                Method method = nowcm.getMethod();
                CmdMeta item = new CmdMeta(nowcmd, method);
                for (Entry<CmdMeta, List<String>> last : lastEntry) {
                    CmdMeta lastcm = last.getKey();
                    if (nowcm.equals(lastcm)) {
                        StageTimeSpanStat nowStat = nowcm.getStat();
                        StageTimeSpanStat lastStat = lastcm.getStat();
                        StageTimeSpanStat itemStat = new StageTimeSpanStat("intervalCmdStat");
                        itemStat.setAllNum(nowStat.getAllNum() - lastStat.getAllNum());
                        itemStat.setAllSpan(nowStat.getAllSpan() - lastStat.getAllSpan());
                        itemStat.setSlowNum(nowStat.getSlowNum() - lastStat.getSlowNum());
                        itemStat.setSlowSpan(nowStat.getSlowSpan() - lastStat.getSlowSpan());
                        itemStat.setMaxSpan(lastStat.getMaxSpan());
                        item.setStat(itemStat);
                        break; // 稍微减少下循环次数,匹配上就跳出内层循环
                    }
                }
                intervalCmdUrlsMap.put(item, url);
            }
            return intervalCmdUrlsMap;
        }

        /**
         * 提取最近10分钟内的请求统计量,0-10分钟范围内数据
         */
        public Map<CmdMeta, List<String>> getLatestCmdUrlsMap() {
            Map<CmdMeta, List<String>> nowCmdUrlsMap = cmdMappers.getReverseCmdAllSortedMap();
            Map<CmdMeta, List<String>> itemCmdUrlsMap = getIntervalCmdUrlsMap(nowCmdUrlsMap, lastCmdUrlsMap);
            return itemCmdUrlsMap;
        }

        /**
         * 返回请求量前三名的cmd字符串,格式为60%[stat/],20%[stat/http],10%[stat/timespan]
         */
        public String getTop3VisitCmd(boolean isGetLastestCmd) {
            Map<CmdMeta, List<String>> cmdUrlsMap = null;
            if (isGetLastestCmd) { // 提取最近10分钟内的top3Cmd数据
                cmdUrlsMap = cmdMappers.getReverseCmdAllSortedMap();
                if (lastCmdUrlsMap != null && lastCmdUrlsMap.size() > 0) {
                    cmdUrlsMap = getIntervalCmdUrlsMap(cmdUrlsMap, lastCmdUrlsMap); // 当前结点和上一次快照间隔统计数据
                }
            } else {
                cmdUrlsMap = last10minCmdUrlsMap;
                if (cmdUrlsMap == null || cmdUrlsMap.size() == 0) { // 第二次快照之前,没有10分钟间隔数据
                    if (lastCmdUrlsMap != null && lastCmdUrlsMap.size() > 0) {
                        cmdUrlsMap = lastCmdUrlsMap; // 已经进行了第一次快照处理,返回第一次快照数据
                    } else { // 连第一次快照都还没进行,返回当前总数据
                        cmdUrlsMap = cmdMappers.getReverseCmdAllSortedMap();
                    }
                }
            }
            Collection<Map.Entry<CmdMeta, List<String>>> entry = cmdUrlsMap.entrySet();
            ArrayList<Map.Entry<CmdMeta, List<String>>> sorting = new ArrayList<Map.Entry<CmdMeta, List<String>>>(entry);
            Collections.sort(sorting, new Comparator<Map.Entry<CmdMeta, List<String>>>() {

                @Override
                public int compare(Map.Entry<CmdMeta, List<String>> o1, Map.Entry<CmdMeta, List<String>> o2) {
                    return (int) (o2.getKey().getStat().getAllNum() - o1.getKey().getStat().getAllNum());
                }
            });
            entry = sorting;
            long allNum = 0;
            for (Entry<CmdMeta, List<String>> e : cmdUrlsMap.entrySet()) {
                allNum += e.getKey().getStat().getAllNum();
            }
            int i = 0;
            StringBuilder sb = new StringBuilder();
            for (Entry<CmdMeta, List<String>> e : entry) {
                CmdMeta meta = e.getKey();
                List<String> url = e.getValue();
                long cmdNum = meta.getStat().getAllNum();
                String percentageStr = allNum == 0 || cmdNum == 0 ? "N/A" : NumberStringUtil.DEFAULT_PERCENT.formatByDivide(cmdNum, allNum);
                sb.append(percentageStr + "[" + url.get(0) + "],");
                if (++i == 3) {
                    break;
                }
            }
            return sb.toString().substring(0, sb.length() - 1);
        }
    }

    @AfterConfig
    protected void init() {
        sche.scheduleAtFixedRateSec(timeSpanStatSnapshotSec);
        otherStatSbapshotQueue = new OtherStatSbapshotQueue("BOUND REQ", statCmd.getInbound(), statCmd.getOutbound(), timeSpanStatQueueSize); // 流量统计队列初始化
    }

    @AfterBootstrap
    protected void defaultRegister() {
        register(statCmd.getProcessTSS(), "HTTP REQ"); // 注册http请求监控任务
    }

    public void register(TimeSpanStat tss, String name) {
        queueMap.put(tss, new TimeSpanStatSbapshotQueue(name, tss, timeSpanStatQueueSize));
    }

    public void unregister(TimeSpanStat tss) {
        queueMap.remove(tss);
    }

    /**
     * <pre>
     * isLimit为是否显示条目
     * limit为要显示的数目,isLimit=true
     * </pre>
     */
    public String getInfo(boolean isLimit, int limit) {
        String fmt_1 = "%-10s %-25s %-25s %-25s %-25s %-25s %-25s %-25s %-25s %-25s";
        String fmt_2 = "%-25s %-25s %-25s %-25s %-25s %-25s %-25s %-25s %-25s %-50s\n";
        StringBuilder tmp = new StringBuilder();
        List<OtherTimeSpanStatResult> streamStatResult = otherStatSbapshotQueue.getResult();
        OtherTimeSpanStatResult lastestOTSSR = otherStatSbapshotQueue.getLastestStatResult(); // 最近10分钟内统计实时数据
        OtherTimeSpanStatResult otssrMax = otherStatSbapshotQueue.getHistoryMaxResult();
        TimeSpanStatResult tssrMax = null;
        for (TimeSpanStatSbapshotQueue q : queueMap.values()) {
            tssrMax = q.getHistoryMaxResult();
            TimeSpanStatResult lastestTSSR = q.getLastestTimeSpanStatResult();
            List<TimeSpanStatResult> httpStatResult = q.getResult();
            if (!isLimit) {
                fmt_1 = "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>";
                fmt_2 = "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n";
                tmp.append("\n").append(getChartUrl(httpStatResult)).append("\n\n");
                tmp.append("<table width=\"220%\"><tbody>\n");
            }
            tmp.append(String.format(fmt_1, "", q.name, "avg_span", "slow_num", "avg_slow_span", "max_span", "tps", "all_num", "all_span", "slow_span"));
            tmp.append(String.format(fmt_2, "load", "avg_decode_bytes", "max_decode_bytes", "avg_encode_bytes", "max_encode_bytes", "all_decode_bytes", "all_decode_num", "all_encode_bytes",
                    "all_encode_num", "top_cmd"));
            tmp.append(lastestTSSR.toString(fmt_1, 0)); // 统计最近10分钟内实时数据更新
            tmp.append(lastestOTSSR.toString(fmt_2)); // 和上边整合成一行显示操作
            int hSize = httpStatResult.size();
            int sSize = streamStatResult.size();
            if (hSize > 0 && hSize == sSize) {
                if (hSize > limit && isLimit) {
                    hSize = limit;
                }
                for (int j = 0; j < hSize; j++) {
                    TimeSpanStatResult tssr = httpStatResult.get(j);
                    OtherTimeSpanStatResult otssr = streamStatResult.get(j);
                    tmp.append(tssr.toString(fmt_1, hSize - j));
                    tmp.append(otssr.toString(fmt_2));
                }
            }
            tmp.append(tssrMax.toString(fmt_1, -1));
            tmp.append(otssrMax.toString(fmt_2));
            tmp.append(tssrMax.toHistoryMaxts(fmt_1)); // 展示历史最大时间的append
            tmp.append(otssrMax.toHistoryMaxts(fmt_2)); // 结合上边合成一行显示的
            if (!isLimit) {
                tmp.append("</tbody></table>");
            }
        }
        return tmp.toString();
    }

    public class OtherStatSbapshotQueue {

        protected String name;
        protected StreamStat inbound;
        protected StreamStat outbound;
        protected ConcurrentCircularQueue<OtherTimeSpanStatResult> queue;
        protected OtherTimeSpanStatSnapshot lastTimeSpanStatSnapshot;
        private OtherTimeSpanStatResult historyOtherTimeSpanStatResult = new OtherTimeSpanStatResult();
        private static final String logfmt = "name:%s,all_decode_bytes:%s,all_decode_num:%s,avg_decode_bytes:%s,max_decode_bytes:%s,all_encode_bytes:%s,all_encode_num:%s,avg_encode_bytes:%s,max_encode_bytes:%s,load:%s,top_cmd:%s";

        public OtherStatSbapshotQueue(String name, StreamStat inbound, StreamStat outbound, int queueSize) {
            this.name = name;
            this.inbound = inbound;
            this.outbound = outbound;
            this.queue = new ConcurrentCircularQueue<OtherTimeSpanStatResult>(queueSize);
            this.snapshot();
        }

        public void snapshot() {
            OtherTimeSpanStatSnapshot thisTimeSpanStatSnapshot = new OtherTimeSpanStatSnapshot(inbound, outbound, true);
            if (lastTimeSpanStatSnapshot != null) {
                OtherTimeSpanStatResult otssr = new OtherTimeSpanStatResult(thisTimeSpanStatSnapshot, lastTimeSpanStatSnapshot, false);
                queue.addToHead(otssr);
                historyOtherTimeSpanStatResult.setMax(otssr);
                timespanStatLog.info(String.format(logfmt, new Object[] {
                    name,
                    HumanReadableUtil.byteSize(otssr.all_decode_bytes),
                    otssr.all_decode_num,
                    HumanReadableUtil.byteSize(otssr.avg_decode_bytes),
                    HumanReadableUtil.byteSize(otssr.max_decode_bytes),
                    HumanReadableUtil.byteSize(otssr.all_encode_bytes),
                    otssr.all_encode_num,
                    HumanReadableUtil.byteSize(otssr.avg_encode_bytes),
                    HumanReadableUtil.byteSize(otssr.max_encode_bytes),
                    otssr.load,
                    otssr.top_cmd
                }));
            }
            lastTimeSpanStatSnapshot = thisTimeSpanStatSnapshot;
        }

        /**
         * 返回当前时间节点和上一次快照节点间隔统计数据
         */
        public OtherTimeSpanStatResult getLastestStatResult() {
            OtherTimeSpanStatSnapshot nowOtherTimeSpanStatSnapshot = new OtherTimeSpanStatSnapshot(inbound, outbound, false);
            if (lastTimeSpanStatSnapshot == null) { // 第一次才会调用操作
                return new OtherTimeSpanStatResult(nowOtherTimeSpanStatSnapshot, true);
            }
            return new OtherTimeSpanStatResult(nowOtherTimeSpanStatSnapshot, lastTimeSpanStatSnapshot, true);
        }

        /**
         * 返回队列里边所有的统计对象
         */
        public List<OtherTimeSpanStatResult> getResult() {
            return queue;
        }

        /**
         * 返回监控统计的历史最大值
         */
        public OtherTimeSpanStatResult getHistoryMaxResult() {
            return historyOtherTimeSpanStatResult;
        }

        /**
         * 流量统计快照类,保存当前的解包发包统计数据
         */
        public class OtherTimeSpanStatSnapshot {

            protected long all_decode_bytes;
            protected long max_decode_bytes;
            protected long all_decode_num;
            protected long all_encode_bytes;
            protected long max_encode_bytes;
            protected long all_encode_num;

            public OtherTimeSpanStatSnapshot(StreamStat inbound, StreamStat outbound, boolean isResetMax) {
                this.all_decode_bytes = inbound.getBytes().get();
                this.all_decode_num = inbound.getNum().get();
                this.max_decode_bytes = inbound.getMax();
                this.all_encode_bytes = outbound.getBytes().get();
                this.all_encode_num = outbound.getNum().get();
                this.max_encode_bytes = outbound.getMax();
                if (isResetMax) { // 只在快照的时候才设置max值
                    inbound.setMax(0l);
                    outbound.setMax(0l);
                }
            }

        }

        public class OtherTimeSpanStatResult {

            protected long ts; // end 时间节点
            protected double load; // 系统负载

            protected long all_decode_bytes;
            protected long all_decode_num;
            protected long avg_decode_bytes; // 平均解码大小
            protected long max_decode_bytes;

            protected long all_encode_bytes;
            protected long all_encode_num;
            protected long avg_encode_bytes; // 平均编码大小
            protected long max_encode_bytes;

            protected String top_cmd = ""; // 保存top3cmd请求百分比字符串

            // ============================
            // 增加历史最大统计值时间
            // ============================

            protected long all_decode_bytes_max_ts;
            protected long all_decode_num_max_ts;
            protected long avg_decode_bytes_max_ts;
            protected long max_decode_bytes_max_ts;

            protected long all_encode_bytes_max_ts;
            protected long all_encode_num_max_ts;
            protected long avg_encode_bytes_max_ts;
            protected long max_encode_bytes_max_ts;

            protected long load_max_ts;

            private OtherTimeSpanStatResult() {
            }

            /**
             * 构造方法,isGetLastestTopcmd为true,提供给统计当前结点和最近一次快照调用
             */
            public OtherTimeSpanStatResult(OtherTimeSpanStatSnapshot now, boolean isGetLastestTopcmd) {
                this.ts = System.currentTimeMillis();
                this.load = SystemMonitor.getLoadAverage();
                this.top_cmd = settingCmdsTSS.getTop3VisitCmd(isGetLastestTopcmd);
                this.all_decode_bytes = now.all_decode_bytes;
                this.all_decode_num = now.all_decode_num;
                this.avg_decode_bytes = all_decode_num > 0 ? all_decode_bytes / all_decode_num : 0;
                this.max_decode_bytes = now.max_decode_bytes;
                this.all_encode_bytes = now.all_encode_bytes;
                this.all_encode_num = now.all_encode_num;
                this.avg_encode_bytes = all_encode_num > 0 ? all_encode_bytes / all_encode_num : 0;
                this.max_encode_bytes = now.max_encode_bytes;
            }

            /**
             * 构造方法,isGetLastestTopcmd含义为false表示正常快照调用,true为统计当前结点和最近一次快照调用
             */
            public OtherTimeSpanStatResult(OtherTimeSpanStatSnapshot end, OtherTimeSpanStatSnapshot begin, boolean isGetLastestTopcmd) {
                this.ts = System.currentTimeMillis();
                this.load = SystemMonitor.getLoadAverage();
                this.top_cmd = settingCmdsTSS.getTop3VisitCmd(isGetLastestTopcmd);
                this.all_decode_bytes = end.all_decode_bytes - begin.all_decode_bytes;
                this.all_decode_num = end.all_decode_num - begin.all_decode_num;
                this.avg_decode_bytes = all_decode_num > 0 ? all_decode_bytes / all_decode_num : 0;
                this.max_decode_bytes = end.max_decode_bytes;
                this.all_encode_bytes = end.all_encode_bytes - begin.all_encode_bytes;
                this.all_encode_num = end.all_encode_num - begin.all_encode_num;
                this.avg_encode_bytes = all_encode_num > 0 ? all_encode_bytes / all_encode_num : 0;
                this.max_encode_bytes = end.max_encode_bytes;
            }

            /**
             * 设置最大值
             */
            public void setMax(OtherTimeSpanStatResult otssr) {
                if (load <= otssr.load) {
                    load = otssr.load;
                    load_max_ts = otssr.ts;
                }
                if (all_decode_bytes <= otssr.all_decode_bytes) {
                    all_decode_bytes = otssr.all_decode_bytes;
                    all_decode_bytes_max_ts = otssr.ts;
                }
                if (all_decode_num <= otssr.all_decode_num) {
                    all_decode_num = otssr.all_decode_num;
                    all_decode_num_max_ts = otssr.ts;
                }
                if (avg_decode_bytes <= otssr.avg_decode_bytes) {
                    avg_decode_bytes = otssr.avg_decode_bytes;
                    avg_decode_bytes_max_ts = otssr.ts;
                }
                if (max_decode_bytes <= otssr.max_decode_bytes) {
                    max_decode_bytes = otssr.max_decode_bytes;
                    max_decode_bytes_max_ts = otssr.ts;
                }
                if (all_encode_num <= otssr.all_encode_num) {
                    all_encode_num = otssr.all_encode_num;
                    all_encode_num_max_ts = otssr.ts;
                }
                if (all_encode_bytes <= otssr.all_encode_bytes) {
                    all_encode_bytes = otssr.all_encode_bytes;
                    all_encode_bytes_max_ts = otssr.ts;
                }
                if (avg_encode_bytes <= otssr.avg_encode_bytes) {
                    avg_encode_bytes = otssr.avg_encode_bytes;
                    avg_encode_bytes_max_ts = otssr.ts;
                }
                if (max_encode_bytes <= otssr.max_encode_bytes) {
                    max_encode_bytes = otssr.max_encode_bytes;
                    max_encode_bytes_max_ts = otssr.ts;
                }
            }

            public String toString(String fmt) {
                return String.format(fmt, load, HumanReadableUtil.byteSize(avg_decode_bytes), HumanReadableUtil.byteSize(max_decode_bytes), HumanReadableUtil.byteSize(avg_encode_bytes),
                        HumanReadableUtil.byteSize(max_encode_bytes), HumanReadableUtil.byteSize(all_decode_bytes), all_decode_num, HumanReadableUtil.byteSize(all_encode_bytes), all_encode_num,
                        top_cmd);
            }

            /**
             * 设置历史最大值各个项对应时间
             */
            public String toHistoryMaxts(String fmt) {
                return String.format(fmt, DateStringUtil.DEFAULT.format(new Date(load_max_ts)), DateStringUtil.DEFAULT.format(new Date(avg_decode_bytes_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(max_decode_bytes_max_ts)), DateStringUtil.DEFAULT.format(new Date(avg_encode_bytes_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(max_encode_bytes_max_ts)), DateStringUtil.DEFAULT.format(new Date(all_decode_bytes_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(all_decode_num_max_ts)), DateStringUtil.DEFAULT.format(new Date(all_encode_bytes_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(all_encode_num_max_ts)), "");
            }

        }
    }

    public class TimeSpanStatSbapshotQueue {

        private String name;
        private TimeSpanStat timeSpanStat;
        private ConcurrentCircularQueue<TimeSpanStatResult> queue;
        private TimeSpanStatSnapshot lastTimeSpanStatSnapshot;
        private TimeSpanStatResult historyTimeSpanStatResult = new TimeSpanStatResult();
        private static final String logfmt = "name:%s,all_num:%s,all_span:%s,avg_span:%s,slow_num:%s,slow_span:%s,avg_slow_span:%s,max_span:%s,tps:%s";

        public TimeSpanStatSbapshotQueue(String name, TimeSpanStat tss, int queueSize) {
            this.name = name;
            this.timeSpanStat = tss;
            this.queue = new ConcurrentCircularQueue<TimeSpanStatResult>(queueSize);
            this.snapshot();
        }

        public void snapshot() {
            TimeSpanStatSnapshot thisTimeSpanStatSnapshot = new TimeSpanStatSnapshot(System.currentTimeMillis(), timeSpanStat); // 当前统计快照
            if (lastTimeSpanStatSnapshot != null) {
                TimeSpanStatResult tssr = new TimeSpanStatResult(thisTimeSpanStatSnapshot, lastTimeSpanStatSnapshot); // 当前这次快照和上一次快照做比较
                queue.addToHead(tssr);
                historyTimeSpanStatResult.setMax(tssr);
                timespanStatLog.info(String.format(logfmt, new Object[] {
                    name,
                    tssr.all_num,
                    HumanReadableUtil.timeSpan(tssr.all_span),
                    HumanReadableUtil.timeSpan(tssr.avg_span),
                    tssr.slow_num,
                    HumanReadableUtil.timeSpan(tssr.slow_span),
                    HumanReadableUtil.timeSpan(tssr.avg_slow_span),
                    HumanReadableUtil.timeSpan(tssr.max_span),
                    tssr.tps
                }));
            }
            lastTimeSpanStatSnapshot = thisTimeSpanStatSnapshot;
            timeSpanStat.setMaxSpan(0l); // 重设为0
        }

        /**
         * 返回实时变化的,最近10分钟内的统计数据
         */
        public TimeSpanStatResult getLastestTimeSpanStatResult() {
            TimeSpanStatSnapshot thisTimeSpanStatSnapshot = new TimeSpanStatSnapshot(System.currentTimeMillis(), timeSpanStat);
            if (lastTimeSpanStatSnapshot == null) { // 提供给第一次实例化的
                return new TimeSpanStatResult(thisTimeSpanStatSnapshot);
            }
            return new TimeSpanStatResult(thisTimeSpanStatSnapshot, lastTimeSpanStatSnapshot);
        }

        /**
         * 返回监控统计快照对象,10分钟统计一次,一天24*6=144次
         */
        public List<TimeSpanStatResult> getResult() {
            return queue;
        }

        /**
         * 返回监控统计历史最高值
         */
        public TimeSpanStatResult getHistoryMaxResult() {
            return historyTimeSpanStatResult;
        }

        public class TimeSpanStatSnapshot { // 统计快照类

            protected long ts;
            protected long all_num;
            protected long all_span;
            protected long slow_num;
            protected long slow_span;
            protected long max_span;

            public TimeSpanStatSnapshot(long ts, TimeSpanStat tss) {
                this.ts = ts;
                this.all_num = tss.getAllNum();
                this.all_span = tss.getAllSpan();
                this.slow_num = tss.getSlowNum();
                this.slow_span = tss.getSlowSpan();
                this.max_span = tss.getMaxSpan();
            }

        }

        public class TimeSpanStatResult {

            protected long ts; // 当前统计end时间
            protected long span; // 统计间隔时间
            protected long all_num; // 请求总次数
            protected long all_span; // 请求总时长
            protected long avg_span; // 平均请求时长
            protected long slow_num; // 慢的总次数
            protected long slow_span; // 慢的总时长
            protected long avg_slow_span; // 平均慢时长
            protected long max_span; // 最大处理时长
            protected long tps; // 每秒处理请求次数

            // ================================
            // 记录下每一个监控元素对应的时间
            // ================================

            protected long all_num_max_ts;
            protected long all_span_max_ts;
            protected long avg_span_max_ts;

            protected long slow_num_max_ts;
            protected long slow_span_max_ts;
            protected long avg_slow_span_max_ts;

            protected long max_span_max_ts;
            protected long tps_max_ts;

            private TimeSpanStatResult() {
            }

            /**
             * 提供给实时获取统计数据的类来实例化的构造方法
             */
            public TimeSpanStatResult(TimeSpanStatSnapshot end) {
                this.ts = end.ts;
                this.span = end.ts;
                this.all_num = end.all_num;
                this.all_span = end.all_span;
                this.avg_span = all_num > 0 ? all_span / all_num : 0;
                this.slow_num = end.slow_num;
                this.slow_span = end.slow_span;
                this.avg_slow_span = slow_num > 0 ? slow_span / slow_num : 0;
                this.max_span = end.max_span;
                this.tps = span > 0 ? this.all_num * 1000 / this.span : 0;
            }

            public TimeSpanStatResult(TimeSpanStatSnapshot end, TimeSpanStatSnapshot begin) {
                this.ts = end.ts;
                this.span = end.ts - begin.ts;
                this.all_num = end.all_num - begin.all_num;
                this.all_span = end.all_span - begin.all_span;
                this.avg_span = all_num > 0 ? all_span / all_num : 0;
                this.slow_num = end.slow_num - begin.slow_num;
                this.slow_span = end.slow_span - begin.slow_span;
                this.avg_slow_span = slow_num > 0 ? slow_span / slow_num : 0;
                this.max_span = end.max_span;
                this.tps = span > 0 ? this.all_num * 1000 / this.span : 0;
            }

            /**
             * 设置最大值,用一个object来保存全部监控元素的最大值
             */
            public void setMax(TimeSpanStatResult tssr) {
                if (all_num <= tssr.all_num) {
                    all_num = tssr.all_num;
                    all_num_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (all_span <= tssr.all_span) {
                    all_span = tssr.all_span;
                    all_span_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (avg_span <= tssr.avg_span) {
                    avg_span = tssr.avg_span;
                    avg_span_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (slow_num <= tssr.slow_num) {
                    slow_num = tssr.slow_num;
                    slow_num_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (slow_span <= tssr.slow_span) {
                    slow_span = tssr.slow_span;
                    slow_span_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (avg_slow_span <= tssr.avg_slow_span) {
                    avg_slow_span = tssr.avg_slow_span;
                    avg_slow_span_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (max_span <= tssr.max_span) {
                    max_span = tssr.max_span;
                    max_span_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
                if (tps <= tssr.tps) {
                    tps = tssr.tps;
                    tps_max_ts = tssr.ts;
                    ts = tssr.ts;
                }
            }

            /**
             * 单为显示历史最大时间值写的方法,感觉有点山寨呀,暂时也没想到其他更好的实现方式
             */
            public String toHistoryMaxts(String fmt) {
                return String.format(fmt, "", "", DateStringUtil.DEFAULT.format(new Date(avg_span_max_ts)), DateStringUtil.DEFAULT.format(new Date(slow_num_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(avg_slow_span_max_ts)), DateStringUtil.DEFAULT.format(new Date(max_span_max_ts)), DateStringUtil.DEFAULT.format(new Date(tps_max_ts)),
                        DateStringUtil.DEFAULT.format(new Date(all_num_max_ts)), DateStringUtil.DEFAULT.format(new Date(all_span_max_ts)), DateStringUtil.DEFAULT.format(new Date(slow_span_max_ts)));
            }

            @Override
            public String toString() {
                String fmt = "%-23s %-23s %-23s %-23s %-23s %-23s %-23s %-23s %-23s";
                return String.format(fmt, DateStringUtil.DEFAULT.format(new Date(ts)), all_num, HumanReadableUtil.timeSpan(all_span), HumanReadableUtil.timeSpan(avg_span),
                        HumanReadableUtil.timeSpan(max_span), slow_num, HumanReadableUtil.timeSpan(slow_span), HumanReadableUtil.timeSpan(avg_slow_span), tps);
            }

            public String toString(String fmt, int idx) {
                String order = String.valueOf(idx);
                if (idx < 0) {
                    order = "max";
                }
                return String.format(fmt, order, DateStringUtil.DEFAULT.format(new Date(ts)), HumanReadableUtil.timeSpan(avg_span), slow_num, HumanReadableUtil.timeSpan(avg_slow_span),
                        HumanReadableUtil.timeSpan(max_span), tps, all_num, HumanReadableUtil.timeSpan(all_span), HumanReadableUtil.timeSpan(slow_span));
            }

        }

    }

    /**
     * <pre>
     * http://www.haijd.net/archive/computer/google/google_chart_api/api.html
     * http://javagooglechart.googlecode.com/
     * </pre>
     */
    private static String getChartUrl(List<TimeSpanStatResult> result) {
        int size = result.size();
        if (size < 3) { // size<3没有必要画图
            return "";
        }
        int[] timesArray = new int[size];
        int[] tpsArray = new int[size];
        int[] avgArray = new int[size];
        int[] slowArray = new int[size];
        for (int i = 0; i < size; i++) {
            TimeSpanStatResult r = result.get(size - 1 - i);
            tpsArray[i] = (int) r.tps;
            avgArray[i] = (int) r.avg_span;
            slowArray[i] = (int) r.slow_num;
            timesArray[i] = (int) r.all_num;
        }
        int bottomSize = Math.min(18, size);// 找了 144 的倍数
        String[] bottom = new String[bottomSize];
        for (int i = 1; i <= bottom.length; i++) {
            bottom[i - 1] = i * size / bottomSize + "";
        }
        if (size / bottomSize > 1) {
            String[] bottom1 = new String[bottom.length + 1];
            bottom1[0] = "1";
            System.arraycopy(bottom, 0, bottom1, 1, bottom.length);
            bottom = bottom1;
        }
        ChartAxis bottomAxis = new ChartAxis(ChartAxis.Bottom, bottom);
        StringBuilder urls = new StringBuilder();
        urls.append(buildChartUrl("times", timesArray, bottomAxis));
        urls.append(buildChartUrl("tps", tpsArray, bottomAxis));
        urls.append(buildChartUrl("avg", avgArray, bottomAxis));
        urls.append(buildChartUrl("slow", slowArray, bottomAxis));
        return urls.toString();
    }

    @SuppressWarnings("unchecked")
    private static String buildChartUrl(String title, int[] array, ChartAxis bottomAxis) {
        boolean needPaint = false;
        for (int a : array) {
            if (a > 0) {
                needPaint = true;
                break;
            }
        }
        if (!needPaint) { // 不需要画图则返回""
            return "";
        }
        LineChart lineChart = new LineChart(1000, 200);
        lineChart.setTitle(title, "0000FF", 14);
        VectorInt datasets = new VectorInt();
        datasets.add(array);
        lineChart.setData(datasets);
        lineChart.setDatasetColors(new String[] {
            "76A4FB"
        });
        lineChart.addAxis(bottomAxis);
        lineChart.addAxis(new ChartAxis(ChartAxis.Left, new String[] {
            "",
            ChartData.findMaxValue(array) + ""
        }));
        String url = lineChart.getUrl();
        return "<img src=\"" + url + "\" alt=\"" + title + "\">";
    }

}
