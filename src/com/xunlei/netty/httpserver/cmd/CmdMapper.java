package com.xunlei.netty.httpserver.cmd;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 * 对一个CMD的请求URL设置别名
 * 注意:
 * CmdPath  处理的是 局部相对路径
 * CmdMapper处理的是 绝对路径
 * 
 * 配置CmdOverride来使用，可以把原来默认的名字覆盖掉
 */
@Target({
    java.lang.annotation.ElementType.TYPE,
    java.lang.annotation.ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CmdMapper {

    public abstract String[] value();

}
