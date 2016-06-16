package com.xunlei.netty.httpserver.cmd.common;

import java.util.Arrays;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Statistics;
import org.slf4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.cmd.CmdOverride;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.spring.AfterConfig;
import com.xunlei.spring.BeanUtil;
import com.xunlei.spring.Config;
import com.xunlei.util.Log;
import com.xunlei.util.NumberStringUtil;

/**
 * @author 曾东
 * @since 2012-5-25 下午4:08:11
 */
@Service
public class StatEhcacheCmd extends BaseStatCmd {

    private static Logger log = Log.getLogger();
    @Config(resetable = true, value = "ehcache.ehCacheMaagerDefaultName")
    private String ehCacheMaagerDefaultName = "ehCacheManager";
    @Config(resetable = true, value = "ehcache.statisticsEnabled")
    private boolean statisticsEnabled = true;

    @AfterConfig
    public void init() {
        // ConcurrentUtil.getDaemonExecutor().schedule(new Runnable() {
        //
        // @Override
        // public void run() {
        statisticsEnabled();
        // }
        // }, 60, TimeUnit.SECONDS);// 因为有Bootstrap.CONTEXT 在 @AfterConfig 拿不到，所以延迟拿 ,60s后应该已经启动完了
    }

    private void statisticsEnabled() {
        try {
            CacheManager ehCacheManager = (CacheManager) BeanUtil.getTypedBean(ehCacheMaagerDefaultName);
            for (String name : ehCacheManager.getCacheNames()) {
                Cache c = ehCacheManager.getCache(name);
                c.setStatisticsEnabled(statisticsEnabled);
            }
            log.info("EhcacheManager          ON,name:{},statisticsEnabled:{},caches:{}", new Object[] {
                ehCacheMaagerDefaultName,
                statisticsEnabled,
                Arrays.toString(ehCacheManager.getCacheNames())
            });
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("EhcacheManager          OFF,name:{}", ehCacheMaagerDefaultName);
        }
    }

    // http://ehcache.org/xref/net/sf/ehcache/Statistics.html
    @CmdAdmin(reportToArmero = true)
    @CmdMapper("/stat/ehcache")
    @CmdOverride
    public Object stat(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        String managerName = request.getParameter("manager", ehCacheMaagerDefaultName);
        boolean enable = request.getParameterBoolean("enable", true);
        CacheManager ehCacheManager = (CacheManager) BeanUtil.getTypedBean(managerName);
        StringBuilder info = new StringBuilder();
        String timeStatFmt = "%-35s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s %-20s\n";
        String tableHeader = String.format(timeStatFmt, "", "hitRate", "hits", "misses", "size", "avgGetTime", "evictionCount", "searchesPerSec", "avgSearchTime", "onDiskHits", "offHeapHits",
                "inMemoryHits", "onDiskMisses", "offHeapMisses", "inMemoryMisses", "diskStoreSize", "offHeapStoreSize", "memStoreSize");
        info.append(tableHeader);
        for (String name : ehCacheManager.getCacheNames()) {
            Cache c = ehCacheManager.getCache(name);
            Statistics s = c.getStatistics();
            long all = s.getCacheHits() + s.getCacheMisses();
            info.append(String.format(timeStatFmt, name, NumberStringUtil.DEFAULT_PERCENT.formatByDivide(s.getCacheHits(), all), s.getCacheHits(), s.getCacheMisses(), c.getSize(),
                    c.getAverageGetTime(), s.getEvictionCount(), c.getSearchesPerSecond(), c.getAverageSearchTime(), s.getOnDiskHits(), s.getOffHeapHits(), s.getInMemoryHits(), s.getOnDiskMisses(),
                    s.getOffHeapMisses(), s.getInMemoryMisses(), c.getDiskStoreSize(), c.getOffHeapStoreSize(), c.getMemoryStoreSize()));

            // 配置文件
            // <cache name="resourceAudit"
            // maxElementsInMemory="20000"
            // maxElementsOnDisk="0"
            // eternal="false"
            // overflowToDisk="false"
            // diskSpoolBufferSizeMB="20"
            // timeToIdleSeconds="60"
            // timeToLiveSeconds="3600"
            // memoryStoreEvictionPolicy="LRU" />

            // cache中本来就有的统计
            // c.getSize();
            // c.getMemoryStoreSize();
            // c.getOffHeapStoreSize();
            // c.getDiskStoreSize();
            // c.getAverageGetTime();
            // c.getAverageSearchTime();
            // c.getSearchesPerSecond();

            // cache中其他可显示项
            // c.getMemoryStoreEvictionPolicy(); // 得到缓存对象占用内存的大小
            // c.getLiveCacheStatistics();
            // c.getCacheConfiguration();
            // c.getStatus();

            // stat中有的统计
            // private final long cacheHits;
            // private final long onDiskHits;
            // private final long offHeapHits;
            // private final long inMemoryHits;
            // private final long misses;
            // private final long onDiskMisses;
            // private final long offHeapMisses;
            // private final long inMemoryMisses;
            // private final long size;
            // private final long memoryStoreSize;
            // private final long offHeapStoreSize;
            // private final long diskStoreSize;
            // private final float averageGetTime;
            // private final long evictionCount;
            // private final long searchesPerSecond;
            // private final long averageSearchTime;
            // private long writerQueueLength;
        }
        return info;
    }
}
