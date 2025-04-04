package com.stream.rtmp.middleware;

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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import org.json.JSONObject;

import com.stream.rtmp.utils.HttpUtil;

import com.stream.rtmp.router.RateLimiter;
import com.stream.rtmp.router.RouteConfig;
import com.stream.rtmp.router.RouteMatcher;

public class HttpAuthMiddleWare extends SimpleChannelInboundHandler<FullHttpRequest>
{
    private RouteMatcher routeMatcher;

    public HttpAuthMiddleWare(RouteMatcher routeMatcher)
    {
        this.routeMatcher = routeMatcher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception
    {
        String plainUri = request.uri();
        String uri = plainUri.split("\\?")[0];
        RouteConfig matchedRoute = routeMatcher.matchRoute(uri);
        String requestMethod = request.method().name().toLowerCase();
        String exactMethod = matchedRoute.method.toLowerCase();

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
            ? HttpUtil.parseRequestParam(request)
            : null;

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

        sendResponse(ctx, HttpResponseStatus.OK, content);
        ctx.fireChannelRead(request.retain());
    }

    private boolean authenticateRequest(FullHttpRequest request)
    {
        return request.headers().contains("Authorization");
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message)
    {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, io.netty.buffer.Unpooled.wrappedBuffer(message.getBytes()));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, message.length());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}