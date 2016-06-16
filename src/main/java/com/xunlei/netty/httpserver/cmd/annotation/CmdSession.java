package com.xunlei.netty.httpserver.cmd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.xunlei.netty.httpserver.util.CmdSessionType;

/**
 * cmd登录态要求
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdSession {

    /** 登录态要求类型 */
    CmdSessionType type();

    /** 对登录态的特殊描述，可以放到这里 */
    String desc() default "";
}
