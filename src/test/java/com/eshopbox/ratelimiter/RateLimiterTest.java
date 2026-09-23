package com.eshopbox.ratelimiter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void shouldAllowUpToConfiguredLimit() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-23T10:00:00Z"),
                ZoneOffset.UTC
        );

        RateLimiter limiter = new RateLimiter(3, clock);

        assertTrue(limiter.allowRequest());
        assertTrue(limiter.allowRequest());
        assertTrue(limiter.allowRequest());
        assertFalse(limiter.allowRequest());
    }

    @Test
    void shouldAllowAgainAfterOneSecondSlidesPast() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-23T10:00:00Z")
        );

        RateLimiter limiter = new RateLimiter(2, clock);

        assertTrue(limiter.allowRequest());
        assertTrue(limiter.allowRequest());
        assertFalse(limiter.allowRequest());

        clock.advanceMillis(1000);

        assertTrue(limiter.allowRequest());
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int limit = 10;
        int threadCount = 100;

        Clock clock = Clock.fixed(
                Instant.parse("2026-09-23T10:00:00Z"),
                ZoneOffset.UTC
        );

        RateLimiter limiter = new RateLimiter(limit, clock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successfulRequests = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();

                    if (limiter.allowRequest()) {
                        successfulRequests.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));

        executor.shutdown();

        assertEquals(limit, successfulRequests.get());
    }

    @Test
    void shouldRejectInvalidLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimiter(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimiter(-1)
        );
    }

    private static class MutableClock extends Clock {

        private Instant currentInstant;

        MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        void advanceMillis(long millis) {
            currentInstant = currentInstant.plusMillis(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
