package com.stream.http.router;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

public class RouteConfig
{
    private static final List<RouteConfig> routes = null;

    public String path;
    public int threshold;
    public long lockPeriod;
    public String method;
    public List<ParamConstraint> params;
    public boolean authentication;

    static class ParamConstraint
    {
        public String key;
        public String type;
        public String pattern;
        public int length;
    }

    public static List<RouteConfig> getRoutes()
    {
        if(routes == null)
        {
            try
            {
                return loadRoutes();
            }
            catch(IOException e)
            {
                System.err.println("[RouteConfig][getRoutes] Error loading routes: "+ e.getMessage());
                throw new RuntimeException("Failed to load routes", e);
            }
        }
        return routes;
    }

    public static List<RouteConfig> loadRoutes() throws IOException
    {
        System.out.println("Working Directory: " + System.getProperty("user.dir"));

        List<RouteConfig> routeList = new ArrayList<>();

        InputStream input = RouteConfig.class.getClassLoader().getResourceAsStream("routerconfigs/routes.json");
        if(input == null)
        {
            throw new FileNotFoundException("Resource not found: routerconfigs/routes.json");
        }

        String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("Routerconfig Loaded JSON: " + content.length());

        System.out.println("Router content:"+ content);
        JSONArray routesArray = new JSONArray(content);

        for(int i = 0; i < routesArray.length(); i++)
        {
            JSONObject routeObj = routesArray.getJSONObject(i);
            RouteConfig route = new RouteConfig();

            route.path = routeObj.getString("path");
            route.threshold = routeObj.getInt("threshold");
            route.lockPeriod = routeObj.getLong("lock-period");
            route.authentication = routeObj.getBoolean("authentication");
            route.method = routeObj.getString("method");
            route.params = new ArrayList<>();

            System.out.println("routepath:"+ route.path +"method:"+ route.method);

            JSONArray paramsArray = routeObj.optJSONArray("params");
            if(paramsArray != null)
            {
                for(int j = 0; j < paramsArray.length(); j++)
                {
                    JSONObject paramObj = paramsArray.getJSONObject(j);
                    RouteConfig.ParamConstraint param = new RouteConfig.ParamConstraint();

                    param.key = paramObj.getString("key");
                    param.type = paramObj.getString("type");
                    param.pattern = paramObj.getString("pattern");
                    param.length = paramObj.getInt("length");
                    
                    System.out.println("param key:"+ param.key + " type:" + param.type + " pattern:" + param.pattern + " length:" + param.length);

                    route.params.add(param);
                }
            }
            routeList.add(route);
        }
        return routeList;
    }
}
