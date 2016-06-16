package com.xunlei.netty.httpserver.util;

import com.xunlei.netty.httpserver.component.XLHttpRequest;

/**
 * @author ZengDong
 * @since 2010-7-20 下午03:47:23
 */
public interface IPGetter {

    public String getIP(XLHttpRequest request);
}
