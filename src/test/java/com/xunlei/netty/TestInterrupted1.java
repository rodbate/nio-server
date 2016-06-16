package com.xunlei.netty;

import java.util.concurrent.Executors;

/**
 * @author ZengDong
 * @since 2010-5-27 上午01:02:52
 */
public class TestInterrupted1 {

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            final int k = i;
            Executors.newSingleThreadExecutor().execute(new Runnable() {

                @Override
                public void run() {
                    if (k != 0) {
                        for (int j = 0; j < 200; j++) {
                            if (Thread.currentThread().isInterrupted()) {
                                System.out.println("asdf");
                                break;
                            }
                        }
                    }
                    // Thread.interrupted();
                    System.out.println("done");
                }

            });
        }

    }
}
