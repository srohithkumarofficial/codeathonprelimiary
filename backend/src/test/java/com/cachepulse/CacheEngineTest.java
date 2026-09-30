package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import org.junit.jupiter.api.Test;

class CacheEngineTest {
    @Test
    void getPutRemoveClearAndSizeBehaveCorrectly() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LRU);
        assertTrue(cache.get("missing").isEmpty());
        cache.put("x", "one");
        cache.put("x", "two");
        cache.put("y", "three");

        assertEquals(2, cache.size());
        assertEquals("two", cache.remove("x").orElseThrow());
        assertFalse(cache.remove("x").isPresent());
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(1, cache.stats().misses());
        assertEquals(1.0, cache.stats().missRate());
    }

    @Test
    void switchingPolicyPreservesEntriesAndCumulativeMetrics() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LRU);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.setPolicy(PolicyType.LFU);

        assertEquals("A", cache.get("a").orElseThrow());
        assertEquals("B", cache.get("b").orElseThrow());
        assertEquals(PolicyType.LFU, cache.stats().policy());
        assertEquals(3, cache.stats().totalRequests());
    }

    @Test
    void zeroRequestRatesAreZero() {
        CacheEngine<String, String> cache = new CacheEngine<>(1, PolicyType.LRU);
        assertEquals(0.0, cache.stats().hitRate());
        assertEquals(0.0, cache.stats().missRate());
    }
}
