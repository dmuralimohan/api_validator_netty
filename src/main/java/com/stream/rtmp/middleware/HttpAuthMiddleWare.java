package com.stream.rtmp.middleware;

import java.util.*;

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
import io.netty.handler.codec.http.DefaultFullHttpResponse;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import org.json.JSONObject;

import com.stream.rtmp.utils.HttpUtil;

public class HttpAuthMiddleWare extends SimpleChannelInboundHandler<FullHttpRequest>
{
    // private static final Set<String> AUTH_REQUIRED_PATHS = Set.of("/secure", "/admin", "/user/profile");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request)
    {
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

        // if(requiresAuthentication(request.uri()) && !isValidToken(request))
        // {
        //     sendUnauthorizedResponse(ctx);
        //     return;
        // }

        //ProcessedRequest processedRequest = new ProcessedRequest(request, queryParams, bodyParams);
        ctx.fireChannelRead(request.retain());
    }

    // private boolean requiresAuthentication(String uri)
    // {
    //     return AUTH_REQUIRED_PATHS.stream().anyMatch(uri::startsWith);
    // }

    private boolean isValidToken(FullHttpRequest request)
    {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        return "Authorization".equals(authHeader);
    }

    private void sendUnauthorizedResponse(ChannelHandlerContext ctx)
    {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}