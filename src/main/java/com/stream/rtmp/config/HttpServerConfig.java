package com.stream.rtmp.config;

import java.util.List;

import java.net.InetSocketAddress;

//import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelInitializer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

import com.stream.rtmp.middleware.HttpAuthMiddleWare;
//import com.example.netty.handler.HttpRequestHandler;

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
                .childHandler(new ChannelInitializer<Channel>()
                {
                    @Override
                    protected void initChannel(Channel ch)
                    {
                        System.out.println("Initializing HTTP Server Channel...");
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(50000));
                        ch.pipeline().addLast(new HttpAuthMiddleWare(routeMatcher));
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
