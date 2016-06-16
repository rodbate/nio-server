package test.com.xunlei.netty;

/**
 * @author ZengDong
 * @since 2010-5-27 上午07:34:42
 */
public class ThreadTimeout extends Thread {

    public long startTimeMillis = 0;

    public volatile boolean finished = false;

    Thread thread;

    public ThreadTimeout() {

        this.startTimeMillis = System.currentTimeMillis();
        System.out.println("startTimeMillis" + startTimeMillis);
        start();
    }

    public static void main(String args[]) {
        ThreadTimeout a = new ThreadTimeout();
        a.doWork();
    }

    @Override
    public void run() {
        while (!finished) {
            timeout();
        }
    }

    private void doWork() {
        int i = 0;
        while (true) {
            if (finished) {
                System.out.println("time out");
                break;
            }
            System.out.println(i++);
        }
    }

    private boolean timeout() {
        long accessTime = System.currentTimeMillis() - startTimeMillis;
        while (accessTime < 600) {
            System.out.println("accessTime   " + accessTime);
            return true;
        }
        finished = true;

        return false;
    }
}
