package com.stream.rtmp.utils;

import java.util.Map;
import java.util.List;

import io.netty.buffer.ByteBuf;

import io.netty.util.CharsetUtil;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.json.JSONObject;
import org.json.JSONException;

public class HttpUtil
{
    public static JSONObject parseRequestBody(FullHttpRequest request)
    {
        ByteBuf content = request.content();
        if(!content.isReadable())
        {
            return new JSONObject();
        }
        try
        {
            return new JSONObject(content.toString(CharsetUtil.UTF_8).trim());
        }
        catch(JSONException e)
        {
            return new JSONObject();
        }
    }

    private static QueryStringDecoder getQueryStringDecoder(FullHttpRequest request)
    {
        return new QueryStringDecoder(request.uri());
    }

    public static Map<String, List<String>> parseRequestParam(FullHttpRequest request)
    {
        return getQueryStringDecoder(request).parameters();
    }

    public static Map<String, List<String>> parseRequestData(FullHttpRequest request)
    {
        HttpMethod method = request.method();
        HttpHeaders headers = request.headers();
        String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);

        if(request.method().equals(HttpMethod.GET))
        {
            return getQueryStringDecoder(request).parameters();
        }
        else if(request.method().equals(HttpMethod.POST) || request.method().equals(HttpMethod.PUT) || request.method().equals(HttpMethod.DELETE) || request.method().equals(HttpMethod.OPTIONS))
        {
            if(request.content().readableBytes() == 0)
            {
                return getQueryStringDecoder(request).parameters();
            }
        }
        else
        {
            return null;
        }
    }
}