package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import java.util.ArrayList;
import java.util.List;
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
                            cache.put(key, operation);
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
        assertEquals(cache.stats().hits() + cache.stats().misses(), cache.stats().totalRequests());
        assertEquals(4_000, cache.stats().totalRequests());
    }
}
