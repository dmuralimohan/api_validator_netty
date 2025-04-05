package com.stream.http.middleware;

import java.util.*;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.net.InetSocketAddress;

import io.netty.util.CharsetUtil;

import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import org.json.JSONObject;

import com.stream.http.utils.HttpUtil;
import com.stream.http.utils.TimeUtil;

import com.stream.http.router.RateLimiter;
import com.stream.http.router.RouteConfig;
import com.stream.http.router.RouteMatcher;

public class HttpAuthMiddleWare extends ChannelDuplexHandler
{
    private RouteMatcher routeMatcher;

    public HttpAuthMiddleWare(RouteMatcher routeMatcher)
    {
        this.routeMatcher = routeMatcher;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        FullHttpRequest request;
        if(msg instanceof FullHttpRequest)
        {
            request = (FullHttpRequest) msg;
        }
        else
        {
            System.out.println("Received non-HTTP message: " + msg);
            return;
        }
        System.out.println("****************************landed middleware: " + TimeUtil.getCurrentTime());

        String plainUri = request.uri();
        if(plainUri.contains("/favicon.ico")) {
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "404 Not Found");
            return;
        }
        String uri = plainUri.split("\\?")[0];
        System.out.println("REQUEST URI IS:"+ plainUri);
        RouteConfig matchedRoute = routeMatcher.matchRoute(uri);
        if(matchedRoute == null)
        {
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "404 Not Found");
            return;
        }
        System.out.println("MATCHED ROUTE IS:"+ matchedRoute.path + " method:" + matchedRoute.method);
        String requestMethod = request.method().name().toLowerCase();
        String exactMethod = matchedRoute.method.toLowerCase();

        System.out.println("****************************after route matched: " + TimeUtil.getCurrentTime());

        System.out.println("landed from auth middleware [uri]"+ plainUri +" method:"+ requestMethod);

        if(matchedRoute == null || !requestMethod.equals(exactMethod))
        {
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "404 Not Found");
            return;
        }

        String clientIp = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        if(!RateLimiter.isAllowed(clientIp, matchedRoute))
        {
            sendResponse(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, "429 Too Many Requests");
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(plainUri);
        Map<String, String> params = new HashMap<>();
        decoder.parameters().forEach((key, value) -> params.put(key, value.get(0)));

        System.out.println("****************************after decoded params:"+ TimeUtil.getCurrentTime());

        System.out.println("params:"+ params);

        if(!routeMatcher.validateParams(matchedRoute, params))
        {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, "400 Invalid Parameters");
            return;
        }

        if(matchedRoute.authentication && !authenticateRequest(request))
        {
            sendResponse(ctx, HttpResponseStatus.UNAUTHORIZED, "401 Unauthorized");
            return;
        }

        HttpHeaders headers = request.headers();
        System.out.println("Headers: " + headers);

        Map<String, String> cookiesMap = null;
        String cookieHeader = headers.get(HttpHeaderNames.COOKIE);
        if(cookieHeader != null)
        {
            cookiesMap = new HashMap<>();
            for(Cookie cookie : ServerCookieDecoder.LAX.decode(cookieHeader))
            {
                cookiesMap.put(cookie.name(), cookie.value());
            }
        }
        System.out.println("Cookies: "+ cookiesMap);

        Map<String, List<String>> queryParams = request.method().equals(HttpMethod.GET)
            ? HttpUtil.parseRequestParam(request) : null;

        String body = null;
        JSONObject bodyParams = null;
        if(request.method().equals(HttpMethod.POST) || request.method().equals(HttpMethod.PUT))
        {
            try
            {
                bodyParams = HttpUtil.parseRequestBody(request);
            }
            catch(Exception e)
            {
                System.out.println("Invalid JSON, using raw body: "+ body);
                bodyParams = new JSONObject();
                bodyParams.put("raw", body);
            }
        }

        InputStream input = RouteConfig.class.getClassLoader().getResourceAsStream("box.json");
        if(input == null)
        {
            throw new FileNotFoundException("Resource not found: routerconfigs/routes.json");
        }

        String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

        sendResponse(ctx, HttpResponseStatus.OK, "helloWorld");
        System.out.println("****************************after response sent: " + TimeUtil.getCurrentTime());
        //ctx.fireChannelRead(request.retain());
        super.channelRead(ctx, request);
    }

    private boolean authenticateRequest(FullHttpRequest request)
    {
        return request.headers().contains("Authorization");
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception
    {
        super.flush(ctx);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String msg)
    {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            ctx.alloc().buffer().writeBytes(msg.getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, msg.length());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}