package com.xunlei.netty.httpserver.cmd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口参数
 * 
 * @author ZengDong
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdParam {

    public static enum Scope {
        GET, POST, COOKIE, GET_OR_POST
    };

    String name();

    String[] desc() default "";

    Class<?> type() default String.class;

    boolean compelled() default true;

    Scope[] scope() default Scope.GET_OR_POST;

    String defaultValue() default "";
}
