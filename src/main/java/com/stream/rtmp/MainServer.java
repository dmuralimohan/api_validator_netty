package com.stream.rtmp;

import com.stream.rtmp.config.HttpServerConfig;

import java.lang.Exception;

public class MainServer
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("Starting Main Server...");

        Thread httpServerThread = new Thread(() ->
        {
            try
            {
                new HttpServerConfig().start();
            }
            catch(Exception e)
            {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        });

        httpServerThread.start();
        httpServerThread.join();

        /*Thread rtmpServerThread = new Thread(() ->
        {
            try
            {
                new RtmpServerConfig().start();
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
            }
        });

        httpServerThread.start();
        rtmpServerThread.start();
        
        httpServerThread.join();
        rtmpServerThread.join();*/
        
        System.out.println("Main Servers Started Successfully!");
    }
}
