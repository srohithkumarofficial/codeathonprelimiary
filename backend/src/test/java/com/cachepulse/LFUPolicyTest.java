package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import org.junit.jupiter.api.Test;

class LFUPolicyTest {
    @Test
    void evictsLeastFrequentlyUsedEntry() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LFU);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.put("c", "C");

        assertTrue(cache.get("b").isEmpty());
        assertTrue(cache.get("a").isPresent());
        assertEquals(1, cache.stats().evictions());
    }

    @Test
    void breaksFrequencyTiesByLeastRecentAccess() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LFU);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.get("b");
        cache.put("c", "C");

        assertTrue(cache.get("a").isEmpty());
        assertTrue(cache.get("b").isPresent());
    }

    @Test
    void replacingEntryResetsItsFrequencyDeterministically() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LFU);
        cache.put("a", "old");
        cache.get("a");
        cache.get("a");
        cache.put("a", "new");
        cache.put("b", "B");
        cache.put("c", "C");

        assertTrue(cache.get("a").isEmpty());
        assertEquals(1, cache.stats().evictions());
    }
}
