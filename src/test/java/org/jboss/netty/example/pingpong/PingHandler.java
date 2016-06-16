package org.jboss.netty.example.pingpong;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;

/**
 * Example of ChannelHandler for the Pong Client
 * 
 * @author frederic
 */
@ChannelPipelineCoverage("one")
public class PingHandler extends SimpleChannelHandler {

    private static final Logger logger = Logger.getLogger(PingHandler.class.getName());

    /**
     * Number of message to do
     */
    private final int nbMessage;

    /**
     * Current rank (decreasing, 0 is the end of the game)
     */
    private int curMessage;

    /**
     * Is there any Ping to send (at least 1 at starting)
     */
    private final AtomicInteger isPing = new AtomicInteger(1);

    /**
     * Start date
     */
    private Date startDate = null;

    /**
     * Stop date
     */
    private Date stopDate = null;

    /**
     * Return value for the caller
     */
    final BlockingQueue<PingPong> answer = new LinkedBlockingQueue<PingPong>();

    /**
     * Ping object
     */
    PingPong pp;

    /**
     * Method to wait for the final PingPong object
     * 
     * @return the final PingPong object
     */
    public PingPong getPingPong() {
        for (;;) {
            try {
                return answer.take();
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    /**
     * Constructor
     * 
     * @param nbMessage
     * @param size
     */
    public PingHandler(int nbMessage, int size) {
        if (nbMessage < 0) {
            throw new IllegalArgumentException("nbMessage: " + nbMessage);
        }
        this.nbMessage = nbMessage;
        curMessage = nbMessage;
        pp = new PingPong(0, new byte[size]);
    }

    /**
     * Add the ObjectXxcoder to the Pipeline
     */
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
        e.getChannel().getPipeline().addFirst("decoder", new ObjectDecoder());
        e.getChannel().getPipeline().addAfter("decoder", "encoder", new ObjectEncoder());
    }

    /**
     * Starts the Ping-Pong
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        logger.log(Level.INFO, "Start PingPong");
        startDate = new Date();
        generatePingTraffic(e);
    }

    /**
     * If write of Ping was not possible before, just do it now
     */
    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
        generatePingTraffic(e);
    }

    /**
     * When the channel is closed, print result
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        stopDate = new Date();
        String MB = String.format("Memory Used: %8.3f MB", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0);
        String Mbs = String.format("%9.3f Mb/s", ((nbMessage - curMessage) * 1000 / (stopDate.getTime() - startDate.getTime()) * (pp.status.length + pp.test1.length() + 16) / 1048576.0 * 8));
        logger.log(Level.INFO, (nbMessage - curMessage) * 2 + " PingPong in " + (stopDate.getTime() - startDate.getTime()) + " ms so " + (nbMessage - curMessage) * 2 * 1000
                / (stopDate.getTime() - startDate.getTime()) + " msg/s (" + Mbs + ") with " + pp.status.length + " bytes in array, " + MB);
    }

    /**
     * When a Pong is received, starts to send the next Ping
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        PingPong pptmp = (PingPong) e.getMessage();
        if (pptmp != null) {
            pp = pptmp;
            isPing.incrementAndGet();
            generatePingTraffic(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        if (e.getCause() instanceof IOException) {
            logger.log(Level.WARNING, "IOException from downstream.");
        } else {
            logger.log(Level.WARNING, "Unexpected exception from downstream.", e.getCause());
        }
        // Offer default object
        answer.offer(pp);
        Channels.close(e.getChannel());
    }

    /**
     * Called when Channel is connected or when the write is enabled again
     * 
     * @param e
     */
    private void generatePingTraffic(ChannelStateEvent e) {
        if (isPing.intValue() > 0) {
            Channel channel = e.getChannel();
            sendPingTraffic(channel);
        }
    }

    /**
     * Called when a Pong message was received
     * 
     * @param e
     */
    private void generatePingTraffic(MessageEvent e) {
        if (isPing.intValue() > 0) {
            Channel channel = e.getChannel();
            sendPingTraffic(channel);
        }
    }

    /**
     * Truly sends the Ping message if any (if not the last one)
     * 
     * @param channel
     */
    private void sendPingTraffic(Channel channel) {
        if ((channel.getInterestOps() & Channel.OP_WRITE) == 0) {
            PingPong sendpp = nextMessage();
            if (sendpp == null) {
                logger.log(Level.WARNING, "Close channel");
                channel.close().addListener(new ChannelFutureListener() {

                    public void operationComplete(ChannelFuture future) {
                        answer.offer(pp);
                    }
                });
                return;
            }
            isPing.decrementAndGet();
            Channels.write(channel, sendpp);
        }
    }

    /**
     * Create the next Ping message if its not the las one.
     * 
     * @return the next Ping message or NULL if it is the last one.
     */
    private PingPong nextMessage() {
        if (curMessage == 0) {
            logger.log(Level.WARNING, "No more message");
            return null;
        }
        curMessage--;
        pp.rank++;
        return pp;
    }
}
