package com.xunlei.netty.httpserver.cmd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口分类描述，用于CMD类上
 * 
 * @since 2012-3-1
 * @author hujiachao
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CmdDescr {

    /**
     * 接口分类描述
     */
    String value();
}
