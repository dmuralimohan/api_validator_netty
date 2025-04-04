package com.stream.rtmp.router;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RateLimiter
{
    private static final ConcurrentHashMap<String, ClientRateState> clientLimits = new ConcurrentHashMap<>();

    static class ClientRateState
    {
        private final long maxRequests;
        private final long timeWindowMs;
        private final long lockPeriodMs;
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.nanoTime());
        private volatile long lockEndTime = 0;

        ClientRateState(long maxRequests, long timeWindowMs, long lockPeriodMs)
        {
            this.maxRequests = maxRequests;
            this.timeWindowMs = timeWindowMs;
            this.lockPeriodMs = lockPeriodMs;
        }

        boolean allowRequest()
        {
            long now = System.currentTimeMillis();
            if(now < lockEndTime)
            {
                return false;
            }

            if(now - windowStart.get() > timeWindowMs)
            {
                windowStart.set(now);
                requestCount.set(0);
            }

            if(requestCount.incrementAndGet() <= maxRequests)
            {
                return true;
            }

            lockEndTime = now + lockPeriodMs;
            return false;
        }
    }

    public static boolean isAllowed(String clientIp, RouteConfig route)
    {
        String key = clientIp + ":" + route.path;
        ClientRateState state = clientLimits.computeIfAbsent(key, k -> new ClientRateState(route.threshold, 60000, route.lockPeriod));
        return state.allowRequest();
    }
}
