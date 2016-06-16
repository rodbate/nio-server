package test.com.xunlei.netty;

/**
 * @author ZengDong
 * @since 2010-5-27 上午01:02:52
 */
public class TestInterrupted {
    // public static void main(String[] args) {
    // Future<Object> future = HttpServerConfig.bizExecutor.submit(new Callable<Object>() {
    // public Object call() throws Exception {
    // int i = 0;
    // while (true) {
    // if (i++ % 10000000 == 0) {
    // System.out.println(System.currentTimeMillis());
    // if(Thread.interrupted()) return null;
    // }
    //
    // }
    // // return null;
    // }
    // });
    // try {
    // future.get(1, TimeUnit.MICROSECONDS);
    // } catch (Exception e) {
    // e.printStackTrace();
    // System.out.println(future.cancel(true));
    // if (e instanceof InterruptedException)
    // Thread.currentThread().interrupt();// 重新声明线程的中断状态
    // }
    // }
}
