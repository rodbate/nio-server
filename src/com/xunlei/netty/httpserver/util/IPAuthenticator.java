package com.xunlei.netty.httpserver.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import org.slf4j.Logger;
import com.xunlei.netty.httpserver.cmd.BaseCmd;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.exception.IpAuthError;
import com.xunlei.spring.BeanUtil;
import com.xunlei.springutil.MailTemplate;
import com.xunlei.util.HttpUtil;
import com.xunlei.util.Log;
import com.xunlei.util.ResourceBundleUtil;
import com.xunlei.util.StringTools;
import com.xunlei.util.codec.DigestUtils;

/**
 * @author ZengDong
 * @since 2010-5-22 上午01:13:09
 */
public class IPAuthenticator {

    private static final Logger log = Log.getLogger();
    private static final Map<String, Long> ACTIVE_MAIL_IP = new HashMap<String, Long>(0);
    private static long ACTIVE_MAIL_KEY = System.nanoTime();
    private static Set<String> ACTIVE_MAIL_LIST = Collections.emptySet();
    private static final long activeIpMs = 1000l * 60 * 60;// 激活成功后的ip的有效期:1小时
    private static final long activeMailKeyChangeNano = 1000l * 1000 * 1000 * 60 * 2;// key定时更新频率,也就是激活邮件的有效期:2分钟
    private static final String activeMailSubject = IPAuthenticator.class.getSimpleName() + "-ActiveMail";
    public static final String DEFAULT_IPFILTER_ALIAS_NAME = "ALIAS.";// 默认的ipfilter别名key前缀配置
    public static final String DEFAULT_IPFILTER_KEY_NAME = "DEFAULT";// 默认的ipfilter配置

    public static final String TEMP_AUTH_MAIL_LIST = "TEMP_AUTH_MAIL_LIST";// 可以远程激活的邮箱地址列表
    public static final String TEMP_AUTH_MAIL_TEMPLATE = "TEMP_AUTH_MAIL_TEMPLATE";// 用于远程激活的com.xunlei.springutil.MailTemplate SpringBean名称
    public static final String TEMP_AUTH_MAIL_TEMPLATE_DEFAULT = "mailTemplate";//

    public static final String DEFAULT_IPFILTER_NAME = "ipfilter";// 默认的ipfilter配置
    private static Set<String> DEFAULT_WHITE_LIST = Collections.emptySet();
    public static final String SPLIT = ",";
    public static boolean LOCALHOST_PASS = true;
    private static MailTemplate mailTemplate;
    private static Map<String, Set<String>> MISC_WHITE_LIST_MAP = Collections.emptyMap();

    static {
        reload(LOCALHOST_PASS);
    }

    public static void auth(BaseCmd cmd, XLHttpRequest request) throws IpAuthError {
        auth(cmd.getClass().getSimpleName(), IPGetterHelper.getIP(request));
    }

    public static void auth(String ip) throws IpAuthError {
        if (DEFAULT_WHITE_LIST.contains("*")) {
            return;
        }
        Long time = ACTIVE_MAIL_IP.get(ip);
        if (time != null) {
            if (System.currentTimeMillis() < time) {
                return;
            }
            ACTIVE_MAIL_IP.remove(ip);
        }
        if (!DEFAULT_WHITE_LIST.contains(ip)) {
            throw IpAuthError.INSTANCE;
        }
    }

    public static void auth(String filterCategory, String ip) throws IpAuthError {
        Set<String> filter = MISC_WHITE_LIST_MAP.get(filterCategory);
        if (filter == null) {
            // if (LOCALHOST_PASS) {
            // if (!HttpUtil.getLocalIP().contains(ip))
            // throw IpAuthError;
            // } else {
            // throw IpAuthError;
            // }

            // 找不到具体filter类别,就有默认的filter
            auth(ip);
        } else {
            if (filter.contains("*")) {
                return;
            }
            if (!filter.contains(ip)) {
                throw IpAuthError.INSTANCE;
            }
        }
    }

    public static void auth(String filterCategory, XLHttpRequest request) throws IpAuthError {
        auth(filterCategory, IPGetterHelper.getIP(request));
    }

    public static void auth(XLHttpRequest request) throws IpAuthError {
        auth(IPGetterHelper.getIP(request));
    }

    public static void authLocalhost(String ip) throws IpAuthError {
        if (!HttpUtil.getLocalIPWith127001().contains(ip)) {
            throw IpAuthError.INSTANCE;
        }
    }

    public static void authLocalhost(XLHttpRequest request) throws IpAuthError {
        authLocalhost(IPGetterHelper.getIP(request));
    }

    public static Set<String> getActiveMailList() {
        return ACTIVE_MAIL_LIST;
    }

    public static String getIPAuthenticatorInfo() {
        return MISC_WHITE_LIST_MAP.toString();
    }

    public static boolean handleActiveMail(String mail, String ip, long time, String key) {
        long now = System.currentTimeMillis();
        String param = "mail=" + mail + "&ip=" + ip + "&time=" + time;
        String key1 = DigestUtils.md5Hex(param + "&" + getCurrentActiveMailKey());
        if (!key1.equals(key)) {
            return false;
        }
        ACTIVE_MAIL_IP.put(ip, now + activeIpMs);
        return true;
    }

    public static void reload() {
        reload(LOCALHOST_PASS);
    }

    public static void reload(boolean localhostPass) {
        LOCALHOST_PASS = localhostPass;
        ResourceBundle b = ResourceBundleUtil.reload(DEFAULT_IPFILTER_NAME);

        Set<String> default_white_list = new LinkedHashSet<String>();
        if (localhostPass) {
            default_white_list.addAll(HttpUtil.getLocalIP());
            default_white_list.add("127.0.0.1");
        }
        Map<String, Set<String>> misc_white_list_map = new HashMap<String, Set<String>>();
        Map<String, Set<String>> alias_map = new HashMap<String, Set<String>>();
        Set<String> active_mail_list = Collections.emptySet();
        if (b != null) {
            Set<String> keySet = b.keySet();

            keySet.remove(TEMP_AUTH_MAIL_TEMPLATE);
            if (keySet.remove(TEMP_AUTH_MAIL_LIST)) {// 初始化可远程调试激活的邮箱
                // 初始化mailTemplate
                String mailTemplateName = b.getString(TEMP_AUTH_MAIL_TEMPLATE);
                if (StringTools.isEmpty(mailTemplateName)) {
                    mailTemplateName = TEMP_AUTH_MAIL_TEMPLATE_DEFAULT;
                }
                try {
                    mailTemplate = BeanUtil.getTypedBean(mailTemplateName);
                    log.info("IPFILTER MAIL ACTIVE ENABLE(Using Bean:{})", mailTemplateName);
                } catch (Throwable e) {
                    mailTemplate = null;
                    log.error("IPFILTER MAIL ACTIVE DISABLE({})", e.toString());
                }

                // 初始化激活邮箱
                String v = b.getString(TEMP_AUTH_MAIL_LIST);
                active_mail_list = (Set<String>) StringTools.splitAndTrim(v, SPLIT, new LinkedHashSet<String>());
            } else {
                log.error("IPFILTER MAIL ACTIVE DISABLE(Not Set Config:TEMP_AUTH_MAIL_LIST)");
            }

            // 初始化别名映射表
            for (String key : keySet) {
                if (key.startsWith(DEFAULT_IPFILTER_ALIAS_NAME)) {
                    String v = b.getString(key);

                    Set<String> list = alias_map.get(key);
                    if (list == null) {
                        list = new LinkedHashSet<String>();
                        alias_map.put(key, list);
                    }
                    list.addAll(StringTools.splitAndTrim(v, SPLIT));
                }
            }

            // 初始化DEFAULT映射表
            if (b.containsKey(DEFAULT_IPFILTER_KEY_NAME)) {
                String v = b.getString(DEFAULT_IPFILTER_KEY_NAME);
                for (String v1 : StringTools.splitAndTrim(v, SPLIT)) {
                    Set<String> trueip = alias_map.get(DEFAULT_IPFILTER_ALIAS_NAME + v1);
                    if (trueip != null) {
                        default_white_list.addAll(trueip);
                    } else {
                        default_white_list.add(v1);
                    }
                }
            }

            // 初始化所有映射表
            for (String key : keySet) {
                if (key.startsWith(DEFAULT_IPFILTER_ALIAS_NAME)) {
                    continue;
                }
                String v = b.getString(key);
                Set<String> list = misc_white_list_map.get(key);
                if (list == null) {
                    list = new LinkedHashSet<String>();
                    if (localhostPass) {
                        list.addAll(HttpUtil.getLocalIP());
                    }
                    misc_white_list_map.put(key, list);
                }
                for (String v1 : StringTools.splitAndTrim(v, SPLIT)) {
                    Set<String> trueip = alias_map.get(DEFAULT_IPFILTER_ALIAS_NAME + v1);
                    if (trueip != null) {
                        list.addAll(trueip);
                    } else {
                        list.add(v1);
                    }
                }
            }
        }
        ACTIVE_MAIL_LIST = active_mail_list;
        DEFAULT_WHITE_LIST = default_white_list;
        MISC_WHITE_LIST_MAP = misc_white_list_map;
    }

    /**
     * 发送激活邮件
     * 
     * @param mail 邮箱地址
     * @param ip 此次要激活的ip地址
     */
    public static boolean sendActiveMail(String mail, String ip) {
        if (mailTemplate != null && ACTIVE_MAIL_LIST.contains(mail)) {
            String param = "mail=" + mail + "&ip=" + ip + "&time=" + System.currentTimeMillis();
            String key = DigestUtils.md5Hex(param + "&" + getCurrentActiveMailKey());
            return mailTemplate.sendTextMail(mail, activeMailSubject, param + "&key=" + key);// TODO:这里把堆栈扔出来是否更合适
        }
        return false;
    }

    private static long getCurrentActiveMailKey() {
        long now = System.nanoTime();
        if (now - ACTIVE_MAIL_KEY > activeMailKeyChangeNano) {
            ACTIVE_MAIL_KEY = now;
        }
        return ACTIVE_MAIL_KEY;

    }
}
