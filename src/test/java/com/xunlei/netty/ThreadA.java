package com.xunlei.netty;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author ZengDong
 * @since 2010-5-27 上午07:36:24
 */
public class ThreadA extends Thread {

    private static final int MAX_TIME = 80;
    private boolean running = true;

    public ThreadA(String name) {
        super(name);
        Timer timer = new Timer();
        EndThreadTask task = new EndThreadTask(this);
        timer.schedule(task, MAX_TIME);
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
            // 业务逻辑
        } catch (InterruptedException e) {
            System.out.println("错误终止");
        }
    }

    public void doWorks() {
        long startTime = System.currentTimeMillis();

        Random rand = new Random(System.nanoTime());
        int delay = rand.nextInt(100);
        while (true) {
            System.out.println("我还在运行呢");
        }
    }

    public void stopRunning() {
        this.running = false;
    }

    public static void main(String args[]) {
        ThreadA a = new ThreadA("a");
        a.start();
    }

}

class EndThreadTask extends TimerTask {

    private ThreadA thread;

    public EndThreadTask(ThreadA thread) {
        this.thread = thread;
    }

    @Override
    public void run() {
        System.out.println(this.thread.getName() + "超时");
        this.thread.interrupt();
    }

}
