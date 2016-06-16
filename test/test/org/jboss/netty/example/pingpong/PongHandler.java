package test.org.jboss.netty.example.pingpong;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;

/**
 * Example of ChannelHandler for the Pong Server
 * 
 * @author frederic
 */
@ChannelPipelineCoverage("one")
public class PongHandler extends SimpleChannelHandler {

    private static final Logger logger = Logger.getLogger(PongHandler.class.getName());

    /**
     * Is there any Pong message to send
     */
    private final AtomicInteger isPong = new AtomicInteger(0);

    /**
     * Bytes monitor
     */
    public static final AtomicLong transferredBytes = new AtomicLong();

    /**
     * Pong object
     */
    private PingPong pp;

    /**
     * Channel Group
     */
    private ChannelGroup channelGroup = null;

    /**
     * Constructor
     * 
     * @param channelGroup
     */
    public PongHandler(ChannelGroup channelGroup) {
        this.channelGroup = channelGroup;
    }

    /**
     * Returns the number of transferred bytes
     * 
     * @return the number of transferred bytes
     */
    public static long getTransferredBytes() {
        return transferredBytes.get();
    }

    /*
     * (non-Javadoc)
     * @see org.jboss.netty.channel.SimpleChannelHandler#channelConnected(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channelGroup.add(ctx.getChannel());
    }

    /**
     * If write of Pong was not possible before, just do it now
     */
    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
        generatePongTraffic(e);
    }

    /**
     * When a Ping message is received, send a new Pong
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        PingPong pptmp = (PingPong) e.getMessage();
        if (pptmp != null) {
            pp = pptmp;
            PongHandler.transferredBytes.addAndGet(pp.status.length + pp.test1.length() + 16);
            isPong.incrementAndGet();
            generatePongTraffic(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.log(Level.WARNING, "Unexpected exception from downstream.", e.getCause());
        Channels.close(e.getChannel());
    }

    /**
     * Used when write is possible
     * 
     * @param e
     */
    private void generatePongTraffic(ChannelStateEvent e) {
        if (isPong.intValue() > 0) {
            Channel channel = e.getChannel();
            sendPongTraffic(channel);
        }
    }

    /**
     * Used when a Ping message is received
     * 
     * @param e
     */
    private void generatePongTraffic(MessageEvent e) {
        if (isPong.intValue() > 0) {
            Channel channel = e.getChannel();
            sendPongTraffic(channel);
        }
    }

    /**
     * Truly send the Pong
     * 
     * @param channel
     */
    private void sendPongTraffic(Channel channel) {
        if ((channel.getInterestOps() & Channel.OP_WRITE) == 0) {
            pp.rank++;
            isPong.decrementAndGet();
            Channels.write(channel, pp);
        }
    }
}
