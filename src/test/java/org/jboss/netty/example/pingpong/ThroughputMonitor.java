package org.jboss.netty.example.pingpong;

/**
 * Class for Throughput Monitoring
 * 
 * @author fbregier
 */
public class ThroughputMonitor extends Thread {

    /**
     * Constructor
     */
    public ThroughputMonitor() {
    }

    @Override
    public void run() {
        try {
            long oldCounter = PongHandler.getTransferredBytes();
            long startTime = System.currentTimeMillis();
            for (;;) {
                Thread.sleep(3000);

                long endTime = System.currentTimeMillis();
                long newCounter = PongHandler.getTransferredBytes();
                System.err.format("%4.3f MiB/s%n", (newCounter - oldCounter) * 1000 / (endTime - startTime) / 1048576.0);
                oldCounter = newCounter;
                startTime = endTime;
            }
        } catch (InterruptedException e) {
            // Stop monitoring asked
            return;
        }
    }
}
