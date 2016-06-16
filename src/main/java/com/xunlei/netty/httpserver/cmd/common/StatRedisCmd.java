package com.xunlei.netty.httpserver.cmd.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;
import com.xunlei.httptool.util.JsonObjectUtil;
import com.xunlei.jedis.JedisTemplate;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.CmdMapper;
import com.xunlei.netty.httpserver.cmd.CmdOverride;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.spring.AfterBootstrap;
import com.xunlei.spring.BeanUtil;
import com.xunlei.util.HumanReadableUtil;

/**
 * @author 曾东
 * @since 2012-6-18 下午8:05:55
 */
@Service
public class StatRedisCmd extends BaseStatCmd {

    private Map<String, JedisTemplate> jedisTemplateMap;

    @AfterBootstrap
    public void init() {
        Map<String, JedisPool> jedisPoolMap = BeanUtil.getTypedBeans(JedisPool.class);
        Map<String, JedisTemplate> jedisTemplateMapTmp = new HashMap<String, JedisTemplate>();
        for (Entry<String, JedisPool> e : jedisPoolMap.entrySet()) {
            String beanName = e.getKey();
            JedisPool pool = e.getValue();
            jedisTemplateMapTmp.put(beanName, new JedisTemplate(pool));
        }
        this.jedisTemplateMap = jedisTemplateMapTmp;
    }

    @CmdOverride
    @CmdMapper("/stat/redis")
    @CmdAdmin(reportToArmero = true)
    public Object process(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        StringBuilder info = new StringBuilder();
        // for (Entry<String, JedisTemplate> e : jedisTemplateMap.entrySet()) {
        // String beanName = e.getKey();
        // JedisTemplate t = e.getValue();
        // info.append(beanName).append(":\n");
        // info.append(t.info());
        // info.append("-----------------------------------------\n\n");
        // }
        List<Map<String, String>> infos = new ArrayList<Map<String, String>>(jedisTemplateMap.size());
        Set<String> keys = new LinkedHashSet<String>();
        keys.add("                                   ");
        for (Entry<String, JedisTemplate> e : jedisTemplateMap.entrySet()) {
            JedisTemplate t = e.getValue();
            Map<String, String> map = t.infoMap();
            // 添加 最大内存 和 内存使用量百分比 缓存策略 dbfilename
            List<String> str = t.configGet("*");
            Map<String, Object> info1 = JsonObjectUtil.buildMap(str.toArray());
            String maxMemoryStr = info1.get("maxmemory").toString();
            String maxmemory_policy = info1.get("maxmemory-policy").toString();
            String dbfilename = info1.get("dbfilename").toString();
            double maxMemory = Double.valueOf(maxMemoryStr);
            double usedMem = Double.valueOf(map.get("used_memory"));
            double ratio = usedMem / maxMemory;
            map.put("maxmemory_policy", maxmemory_policy);
            map.put("maxMemory", HumanReadableUtil.byteSize((long) maxMemory));
            map.put("ratio", HumanReadableUtil.percentStrSimple(ratio));
            map.put("dbfilename", dbfilename);

            infos.add(map);
            keys.addAll(map.keySet());
        }
        // 添加 最大内存 和 内存使用量百分比 缓存策略
        keys.add("maxmemory_policy");
        keys.add("maxMemory");
        keys.add("ratio");
        keys.add("dbfilename");

        StringBuilder tableHeader = new StringBuilder();
        for (String k : keys) {
            String content = infos.get(0).get(k);
            if (infos.size() < 1 || content == null) {
                tableHeader.append("%-").append(Math.max(10, k.length())).append("s ");
            } else {
                tableHeader.append("%-").append(Math.max(Math.max(10, k.length()), content.length())).append("s ");
            }
        }
        String fmt = tableHeader.append("\n").toString();
        info.append(String.format(fmt, keys.toArray())); // 打印表头

        int infosIndex = 0;
        for (Entry<String, JedisTemplate> e : jedisTemplateMap.entrySet()) {
            String beanName = e.getKey();
            Map<String, String> map = infos.get(infosIndex++);
            Object[] args = new String[keys.size()];
            Iterator<String> iterator = keys.iterator();
            iterator.next();
            args[0] = beanName;

            for (int i = 1; iterator.hasNext(); i++) {
                String key = iterator.next();
                String value = map.get(key);
                args[i] = value == null ? "N/A" : value;
            }
            info.append(String.format(fmt, args));
        }
        return info;
    }
}
