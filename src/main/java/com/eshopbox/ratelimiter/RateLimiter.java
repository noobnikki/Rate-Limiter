package com.eshopbox.ratelimiter;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;


public class RateLimiter {

    private static final long WINDOW_MILLIS = 1000;

    private final int limit;
    private final Clock clock;
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    public RateLimiter(int limit) {
        this(limit, Clock.systemUTC());
    }

    RateLimiter(int limit, Clock clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }

        this.limit = limit;
        this.clock = clock;
    }

    public synchronized boolean allowRequest() {
        long now = clock.millis();

        while (!requestTimestamps.isEmpty()
                && now - requestTimestamps.peekFirst() >= WINDOW_MILLIS) {
            requestTimestamps.pollFirst();
        }

        if (requestTimestamps.size() >= limit) {
            return false;
        }

        requestTimestamps.addLast(now);
        return true;
    }
}
