package test.com.xunlei.netty;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author ZengDong
 * @since 2010-5-22 下午04:00:24
 */
public class Testaa {

    public static void main(String[] args) {
        for (int j = 0; j < 10000; j++) {
            // Future<String> future = Executors.newCachedThreadPool();
            // Future<String> future = HttpServerConfig.bizExecutor.submit(new Callable<String>() {
            Future<String> future = Executors.newCachedThreadPool().submit(new Callable<String>() {

                @Override
                public String call() throws Exception {

                    Thread.sleep(Integer.MAX_VALUE);
                    return null;
                }
            });
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread());
            } catch (ExecutionException e) {

                System.out.println(future.cancel(true));
            } catch (TimeoutException e) {
                System.out.println(future.cancel(true));

                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] infos = threadMXBean.dumpAllThreads(true, true);
                StringBuilder tmp = new StringBuilder();
                String fmt = "%-8s%-14s%-8s%-8s%s\n";
                tmp.append(String.format(fmt, "ID", "STATE ", "Blocks", "Waits", "Name"));
                for (int i = 0; i < infos.length; i++) {
                    ThreadInfo info = infos[i];
                    tmp.append(String.format(fmt, info.getThreadId(), info.getThreadState(), info.getBlockedCount(), info.getWaitedCount(), info.getThreadName()));
                }

                System.out.println(tmp);
            }
        }

    }
}
