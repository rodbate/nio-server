package test.com.xunlei.netty;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

/**
 * @author ZengDong
 * @since 2010-5-23 下午08:05:03
 */
public class TestExecutor {

    private static class NamedThreadFactory implements ThreadFactory {

        final ThreadGroup group;
        int priority = Thread.NORM_PRIORITY;

        NamedThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, "test", 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            t.setPriority(priority);
            // if (t.getPriority() != Thread.NORM_PRIORITY)
            // t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    public static void main(String[] args) {
        // ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);
        // System.out.println(e.getCorePoolSize());
        // System.out.println(e.getMaximumPoolSize());
        // System.out.println(e.getActiveCount());
        // System.exit(1);

        final OrderedMemoryAwareThreadPoolExecutor pipelineExecutor = new OrderedMemoryAwareThreadPoolExecutor(2000, 1048576, 1073741824, 100, TimeUnit.MILLISECONDS, new NamedThreadFactory());
        // pipelineExecutor.setMaximumPoolSize(2000);
        System.out.println(pipelineExecutor.getCorePoolSize());
        System.out.println(pipelineExecutor.getMaximumPoolSize());
        System.out.println(pipelineExecutor.getActiveCount());
        // System.exit(1);
        pipelineExecutor.execute(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    System.out.println("getActiveCount:" + pipelineExecutor.getActiveCount() + ",queue:" + pipelineExecutor.getQueue().size());
                }

            }
        });

        final AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 80000; i++) {
            final int k = i;
            try {
                Thread.sleep(2);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            pipelineExecutor.execute(new Runnable() {

                @Override
                public void run() {
                    Thread.currentThread().stop();
                    // if (k == 1000)
                    if (!Thread.currentThread().getName().equals("test")) {
                        Thread.dumpStack();
                    }
                    System.out.println("AS");
                    // System.out.println(counter.incrementAndGet());
                    try {
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException e) {
                        // e.printStackTrace();
                    }
                }
            });
        }

    }

    public static Object threads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = threadMXBean.dumpAllThreads(true, true);
        StringBuilder tmp = new StringBuilder();
        String fmt = "%-8s%-14s%-8s%-8s%s\n";
        tmp.append(String.format(fmt, "ID", "STATE ", "Blocks", "Waits", "Name"));
        for (int i = 0; i < infos.length; i++) {
            ThreadInfo info = infos[i];
            tmp.append(String.format(fmt, info.getThreadId(), info.getThreadState(), info.getBlockedCount(), info.getWaitedCount(), info.getThreadName()));
        }
        tmp.append("\n");
        tmp.append("\n");
        for (int i = 0; i < infos.length; i++) {
            tmp.append("--------------- (");
            tmp.append(i);
            tmp.append(") ------------------------------------------------------------------------------------------------------------------------\n");
            ThreadInfo info = infos[i];
            tmp.append(info.toString());
        }
        return tmp.toString();
    }
}
