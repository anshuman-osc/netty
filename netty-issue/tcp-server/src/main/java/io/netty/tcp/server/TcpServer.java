/*
 * Copyright (c) 2003 - 2017 Tyro Payments Limited.
 * Lv1, 155 Clarence St, Sydney NSW 2000.
 * All rights reserved.
 */
package io.netty.tcp.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.codec.xml.XmlFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;

public class TcpServer {

    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);

    private int port;
    private int acceptThreadCount;
    private int ioThreadCount;

    private NioEventLoopGroup acceptGroup;
    private NioEventLoopGroup ioGroup;
    private ServerBootstrap bootstrap;
    private Channel serverChannel;
    private SSLContext sslContext;
    private ExecutorService service;


    public static void main(String[] args) {

        try {
            TcpServer server = new TcpServer();
            server.acceptThreadCount = 1;
            server.ioThreadCount = 8;
            server.port = 8000;

            server.start();
            server.serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            System.exit(-1);
        }
    }

    public void start() {
        try {
            service = Executors.newFixedThreadPool(2000);
            sslContext = SslContextFactory.createJdkSSLContext("/server-keystore.jks", "/server-keystore.jks");
            acceptGroup = new NioEventLoopGroup(acceptThreadCount);
            ioGroup = new NioEventLoopGroup(ioThreadCount);
            bootstrap = new ServerBootstrap();
            bootstrap.group(acceptGroup, ioGroup)
                    .localAddress(port)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(getChannelInitializer())
                    .option(ChannelOption.SO_LINGER, 0)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_KEEPALIVE, false);

            ChannelFuture bindFuture = bootstrap.bind().sync();
            serverChannel = bindFuture.channel();
            LOG.info("server started on port: " + port);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Server", e);
        }
    }

    private ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                LOG.info("initializing");
                ch.pipeline().addLast(new SslHandler(SslContextFactory.createSslEngine(sslContext)));
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                ch.pipeline().addLast(new XmlFrameDecoder(65 * 1024));
                ch.pipeline().addLast(new StringDecoder());
                ch.pipeline().addLast(new StringEncoder());
                ch.pipeline().addLast(new EchoHandler());
            }
        };
    }

    private class EchoHandler extends SimpleChannelInboundHandler<String> {

        private final Logger LOG = LoggerFactory.getLogger(EchoHandler.class);

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final String msg) throws Exception {
            service.submit(() -> {
                try {
                    TimeUnit.SECONDS.sleep(1); // simulate business logic delay
                } catch (InterruptedException e) {
                    LOG.error("Interrupted", e);
                }
                LOG.debug("Responding with: {}", msg);
                ctx.writeAndFlush(msg);
            });
        }
    }
}
