package com.xunlei.netty.httpserver.cmd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ZengDong
 * 
 *         <pre>
 * 接口有两种：
 * 业务相关【用户用】
 * 系统相关【开发用】  用@CmdAdmin来标识
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdAdmin {

    public enum CmdAdminType {
        /**
         * 后台统计接口
         */
        STAT,
        /**
         * 后台操作接口
         */
        OPER,
    }

    /**
     * 是否上报到 armero，供统一查看【一般比较常用接口上报到armero】
     */
    boolean reportToArmero() default false;

    CmdAdminType type() default CmdAdminType.STAT;
}
