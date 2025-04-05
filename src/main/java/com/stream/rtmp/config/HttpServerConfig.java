package com.stream.rtmp.config;

import java.util.List;

import java.net.InetSocketAddress;

//import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelInitializer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

import com.stream.rtmp.middleware.HttpAuthMiddleWare;
//import com.example.netty.handler.HttpRequestHandler;

import com.stream.rtmp.utils.TimeUtil;

import com.stream.rtmp.router.RouteConfig;
import com.stream.rtmp.router.RouteMatcher;

public class HttpServerConfig
{
    private static final int PORT = 8080;

    public void start() throws Exception
    {
        List<RouteConfig> HttpRoutes = RouteConfig.loadRoutes();
        RouteMatcher routeMatcher = new RouteMatcher(HttpRoutes);

        EventLoopGroup bossGroup = new NioEventLoopGroup(2);
        EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);

        try
        {
            System.out.println("Starting HTTP Server...");
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<Channel>()
            {
                @Override
                protected void initChannel(Channel ch)
                {
                    long initStart = System.nanoTime();

                    System.out.println("🔷 Request landed at server config: " + TimeUtil.getCurrentTime());
                    System.out.println("📡 Initializing HTTP Server Channel...");

                    ChannelPipeline pipeline = ch.pipeline();

                    pipeline.addLast("httpCodec", new HttpServerCodec());
                    pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));
                    pipeline.addLast("authMiddleware", new HttpAuthMiddleWare(routeMatcher));

                    long initEnd = System.nanoTime();
                    System.out.println("✅ Pipeline init done in " + ((initEnd - initStart) / 1_000_000.0) + " ms");
                }
            });

            ChannelFuture future = bootstrap.bind("0.0.0.0", PORT).sync();
            Channel channel = future.channel();
            InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
            String ip = socketAddress.getAddress().getLocalHost().toString();
            int port = socketAddress.getPort();
            System.out.println("HTTP Server started on "+ ip +":"+ port);
            future.channel().closeFuture().sync();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
