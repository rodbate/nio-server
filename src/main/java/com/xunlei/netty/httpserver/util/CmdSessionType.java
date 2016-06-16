package com.xunlei.netty.httpserver.util;

/**
 * cmd的登录态设定
 * 
 * @since 2012-6-13
 * @author hujiachao
 */
public enum CmdSessionType {
    /** 必须要有登录态 */
    COMPELLED,
    /** 不需要登录态 */
    NOT_COMPELLED,
    /** 有登录态和没有登录态时都能正常使用，如果没有登录态会被当作游客处理 */
    DISPENSABLE
}
