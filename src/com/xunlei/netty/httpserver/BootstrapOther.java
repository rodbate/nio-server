package com.xunlei.netty.httpserver;

/**
 * <pre>
 * BootstrapOther 用于在启动时绑定监听端口阶段，对其他业务端口的绑定操作（如thriftServer）
 * 
 * 注意springContext全局只能有一个BootstrapOther
 * 
 * @author 曾东
 * @since 2012-5-30 上午10:53:32
 */
public interface BootstrapOther {

    public void bind();
}
