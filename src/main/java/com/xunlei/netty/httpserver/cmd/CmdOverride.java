package com.xunlei.netty.httpserver.cmd;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置CmdPath或CmdMapper来作用，覆盖掉原来的默认名
 * 
 * @author ZengDong
 * @since 2012-3-28 上午11:33:00
 */
@Target({
    java.lang.annotation.ElementType.TYPE,
    java.lang.annotation.ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CmdOverride {
}
