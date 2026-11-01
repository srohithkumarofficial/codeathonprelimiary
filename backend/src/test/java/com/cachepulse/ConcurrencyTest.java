package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {
    @Test
    void concurrentMixedAccessPreservesCapacityAndMetrics() throws Exception {
        CacheEngine<Integer, Integer> cache = new CacheEngine<>(64, PolicyType.LFU);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                int worker = thread;
                jobs.add(executor.submit(() -> {
                    for (int operation = 0; operation < 1_000; operation++) {
                        int key = (operation + worker) % 100;
                        if ((operation & 1) == 0) {
                            cache.put(key, key);
                        } else {
                            cache.get(key);
                        }
                    }
                }));
            }
            for (Future<?> job : jobs) {
                job.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(cache.size() <= 64);
        assertEquals(4_000, cache.stats().totalRequests());
        for (int key = 0; key < 100; key++) {
            int expectedKey = key;
            cache.get(key).ifPresent(value -> assertEquals(expectedKey, value));
        }
        assertEquals(cache.stats().hits() + cache.stats().misses(), cache.stats().totalRequests());
        assertEquals(4_100, cache.stats().totalRequests());
    }

    @Test
    void concurrentTtlExpiryAndEvictionPreserveCacheInvariants() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        CacheEngine<Integer, Integer> cache = new CacheEngine<>(8, PolicyType.LFU, clock);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                int worker = thread;
                jobs.add(executor.submit(() -> {
                    for (int operation = 0; operation < 500; operation++) {
                        now.updateAndGet(current -> current.plusMillis(1));
                        int key = (operation + worker) % 16;
                        if (operation % 3 == 0) {
                            cache.put(key, key, Duration.ofMillis(5));
                        } else {
                            int expectedKey = key;
                            cache.get(key).ifPresent(value -> assertEquals(expectedKey, value));
                        }
                    }
                }));
            }
            for (Future<?> job : jobs) job.get();
        } finally {
            executor.shutdownNow();
        }

        assertTrue(cache.size() <= 8);
        assertTrue(cache.stats().expirations() + cache.stats().evictions() > 0);
        assertEquals(1_332, cache.stats().totalRequests());
        assertEquals(cache.stats().totalRequests(), cache.stats().hits() + cache.stats().misses());
    }
}
