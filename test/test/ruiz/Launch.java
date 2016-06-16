package test.ruiz;

import java.io.IOException;
import com.xunlei.netty.httpserver.Bootstrap;
import com.xunlei.netty.httpserver.util.HttpServerConfig;

/**
 * @author ZengDong
 * @since 2010-4-5 上午11:23:24
 */
public class Launch {

    public static void main(String[] args) throws IOException {
        Bootstrap.main(args, new Runnable() {

            @Override
            public void run() {

                // 定时重加载配置
                // final ConfigAnnotationBeanPostProcessor p = BeanUtil.getTypedBean(Bootstrap.CONTEXT, "configAnnotationBeanPostProcessor");
                // ConcurrentUtil.getDaemonExecutor().scheduleWithFixedDelay(new Runnable() {
                // public void run() {
                // p.reloadConfig();
                // IPAuthenticator.reload();
                // }
                // }, 5, 5, TimeUnit.SECONDS);

            }
        }, new Runnable() {

            @Override
            public void run() {
                // 在关闭httpSever时,走安全关闭的步骤时,须关闭内部的boss线程跟worker线程
                // 可能关闭会很慢
                HttpServerConfig.releaseExternalResources();
            }
        }, "classpath:/test/ruiz/applicationContext.xml");
    }
}
