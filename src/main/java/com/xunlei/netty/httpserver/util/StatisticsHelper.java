package com.xunlei.netty.httpserver.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.common.StatCmd;
import com.xunlei.spring.AfterConfig;
import com.xunlei.util.DateStringUtil;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * <pre>
 * 提供对httpServer中tps的简单实时统计
 * 
 * 注意：如果stat重置了,在那段时间的tps会显示成负数,不处理这种情况
 * 
 * @author ZengDong
 * @since 2011-1-15 下午06:39:05
 */
@Service
public class StatisticsHelper {

    // private static final Logger log = Log.getLogger();
    /**
     * 快照类
     */
    public class Snapshot {

        private Date date;// 统计时当前时间
        private long num;// 统计时当前数目

        private Snapshot(long num) {
            this.date = new Date();
            this.num = num;
        }

        private Snapshot(Date date, long num) {
            this.date = date;
            this.num = num;
        }

        public Date getDate() {
            return date;
        }

        public long getNum() {
            return num;
        }

        @Override
        public String toString() {
            return DateStringUtil.DEFAULT.format(date) + "   " + num;
        }
    }

    @AfterConfig
    public synchronized void init() {
        if (init) {
            return;
        }
        maxTps = snapshotArray[snapshotIndex++ % snapshotSize] = new Snapshot(0);// 此行可以注释掉,注释掉的话是没有记录开机0记录的情况
        ConcurrentUtil.getDaemonExecutor().scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    Snapshot dss = new Snapshot(statCmd.getProcessTSS().getAllNum());
                    snapshotArray[snapshotIndex++ % snapshotSize] = dss;
                    // log.error(dss.toString());
                    Snapshot dss0 = snapshotArray[snapshotIndex - 2 % snapshotSize];// 第一次时,会报错,忽略
                    lastTps = (int) calcTps(dss, dss0);
                    if (maxTps.getNum() <= lastTps) {
                        maxTps = new Snapshot(dss.getDate(), lastTps);
                    }
                } catch (Exception e) {
                    // log.error("", e);
                }
            }
        }, getSnapshotInitialDelay(), snapshotMilliseconds, TimeUnit.MILLISECONDS);
        init = true;
    }

    private boolean init;
    @Autowired
    private StatCmd statCmd;
    private static final int dayms = 3600 * 1000 * 24;
    // @Config
    private int snapshotDay = 30; // 内存记录下几天的快照记录
    private int snapshotMilliseconds = 600000;// 每隔10min记录一次
    // private int snapshotMilliseconds = 10000;
    private int snapshotSize = dayms / snapshotMilliseconds * snapshotDay;// 每隔10min记录一次,所以每小时采样6个点,内存记录30天的记录
    private Snapshot[] snapshotArray = new Snapshot[snapshotSize];
    private int snapshotIndex = 0;
    private Snapshot maxTps;// 历史最高记录
    private int lastTps = -1;// 上次统计到的tps

    public int getLastTps() {
        return lastTps;
    }

    private long getSnapshotInitialDelay() {// 实现趋近于系统时间的 定点时间功能
        String truncateDateStr = DateStringUtil.DEFAULT.now().substring(0, 15) + "0:00";// 这是每隔10min
        // String truncateDateStr = DateStringUtil.DEFAULT.now().substring(0, 18) + "0";// 这是每隔10sec
        Date truncateDate = DateStringUtil.DEFAULT.parse(truncateDateStr);
        return truncateDate.getTime() + snapshotMilliseconds - System.currentTimeMillis();
    }

    /**
     * @param sampleSize 要计算tps的采样数
     * @param interval 采样毫秒数间隔(小于snapshotMilliseconds时则按snapshotMilliseconds计算)
     * @param calcTps true表示计算tps,false表示显示原始计数
     */
    public List<Snapshot> getSnapshot(int sampleSize, long interval, boolean calcTps) {
        int currentSnapshotIndex = snapshotIndex;// 因为在查询当前结果时,访问量大的话snapshotIndex一直在递增,所以要先保存这个idx
        int step = (int) (interval / snapshotMilliseconds);// 算出采样步进
        if (step <= 0) {
            step = 1;
        }

        int size = sampleSize * step;// size指数组里面相关的数据的总跨度
        if (size > snapshotSize) {
            size = snapshotSize;
        }
        int beginIndex = currentSnapshotIndex - size;

        List<Snapshot> statList = new ArrayList<Snapshot>();
        if (calcTps) {
            if (beginIndex < step) {
                beginIndex = step;
            }
            for (int i = beginIndex; i < currentSnapshotIndex; i = i + step) {
                Snapshot dss0 = snapshotArray[(i - step) % snapshotSize];
                Snapshot dss = snapshotArray[i % snapshotSize];
                statList.add(new Snapshot(dss.getDate(), calcTps(dss, dss0)));
            }
        } else {
            if (beginIndex < 0) {
                beginIndex = 0;
            }
            for (int i = beginIndex; i < currentSnapshotIndex; i = i + step) {
                Snapshot dss = snapshotArray[i % snapshotSize];
                statList.add(dss);
            }
        }
        return statList;
    }

    private static long calcTps(Snapshot dss, Snapshot dss0) {
        long num = dss.getNum() - dss0.getNum();
        long sec = (dss.getDate().getTime() - dss0.getDate().getTime()) / 1000;
        if (sec <= 0) {
            return 0;
        }
        return num / sec;
    }

    public Snapshot getMaxTps() {
        return maxTps;
    }

    @Override
    public String toString() {
        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < snapshotIndex; i++) {
            if (snapshotArray[i] == null) {
                break;
            }
            tmp.append(snapshotArray[i]).append("\n");
        }
        return tmp.toString();
    }
}
