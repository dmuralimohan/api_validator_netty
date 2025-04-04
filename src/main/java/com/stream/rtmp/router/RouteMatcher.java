package com.stream.rtmp.router;

import java.util.regex.*;
import java.util.*;

public class RouteMatcher
{
    private final List<RouteConfig> routes;

    public RouteMatcher(List<RouteConfig> routes)
    {
        this.routes = routes;
    }

    public RouteConfig matchRoute(String requestPath)
    {
        for(RouteConfig route : routes)
        {
            String regexPattern = route.path.replace("{{", "(?<").replace("}}", ">[^/]+)");
            Pattern pattern = Pattern.compile("^" + regexPattern + "$");
            Matcher matcher = pattern.matcher(requestPath);
            if(matcher.matches())
            {
                return route;
            }
        }
        return null;
    }

    public boolean validateParams(RouteConfig route, Map<String, String> requestParams)
    {
        System.out.println("requestParams"+ route.params);
        for(RouteConfig.ParamConstraint param : route.params)
        {
            String value = requestParams.get(param.key);
            System.out.println("param:"+ param.key + " value:" + value);
            if(param.equals("method"))
            {
                continue;
            }
            
            if(value == null)
            {
                return false;
            }

            if(param.type.equals("string") && !value.matches(param.pattern))
            {
                return false;
            }
            if(value.length() > param.length)
            {
                return false;
            }
        }
        return true;
    }
}
