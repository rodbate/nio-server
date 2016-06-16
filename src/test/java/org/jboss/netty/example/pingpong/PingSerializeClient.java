package org.jboss.netty.example.pingpong;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

/**
 * Simple example of Ping-Pong Client using Serialization
 * 
 * @author frederic
 */
public class PingSerializeClient {

    /**
     * Main class for Client taking from two to four arguments<br>
     * -host for server<br>
     * -port for server<br>
     * -number of message (default is 256)<br>
     * -size of array of bytes (default is 16384 = 16KB)
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        // if (args.length < 2 || args.length > 4) {
        // System.err
        // .println("Usage: " +
        // PingSerializeClient.class.getSimpleName() +
        // " <host> <port> [<number of messages>] [<size of array of bytes>]");
        // return;
        // }
        //
        // // Parse options.
        // String host = args[0];
        // int port = Integer.parseInt(args[1]);
        String host = "localhost";
        int port = 1986;
        int nbMessage;

        if (args.length >= 3) {
            nbMessage = Integer.parseInt(args[2]);
        } else {
            nbMessage = 256;
        }
        int size = 16384;
        if (args.length == 4) {
            size = Integer.parseInt(args[3]);
        }

        // *** Start the Netty configuration ***

        // Start client with Nb of active threads = 3 as maximum.
        ChannelFactory factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 3);
        // Create the bootstrap
        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        // Create the global ChannelGroup
        ChannelGroup channelGroup = new DefaultChannelGroup(PingSerializeClient.class.getName());
        // Create the associated Handler
        PingHandler handler = new PingHandler(nbMessage, size);

        // Add the handler to the pipeline and set some options
        bootstrap.getPipeline().addLast("handler", handler);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        bootstrap.setOption("reuseAddress", true);
        bootstrap.setOption("connectTimeoutMillis", 100);

        // *** Start the Netty running ***

        // Connect to the server, wait for the connection and get back the channel
        Channel channel = bootstrap.connect(new InetSocketAddress(host, port)).awaitUninterruptibly().getChannel();
        // Add the parent channel to the group
        channelGroup.add(channel);
        // Wait for the PingPong to finish
        PingPong pingPong = handler.getPingPong();
        System.out.println("Result: " + pingPong.toString() + " for 2x" + nbMessage + " messages and " + size + " bytes as size of array");

        // *** Start the Netty shutdown ***

        // Now close all channels
        System.out.println("close channelGroup");
        channelGroup.close().awaitUninterruptibly();
        // Now release resources
        System.out.println("close external resources");
        factory.releaseExternalResources();
    }
}
