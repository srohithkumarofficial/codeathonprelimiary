package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import org.junit.jupiter.api.Test;

class LRUPolicyTest {
    @Test
    void evictsLeastRecentlyUsedEntry() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LRU);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.put("c", "C");

        assertTrue(cache.get("b").isEmpty());
        assertTrue(cache.get("a").isPresent());
        assertEquals(1, cache.stats().evictions());
    }

    @Test
    void updatingExistingKeyDoesNotIncreaseSize() {
        CacheEngine<String, String> cache = new CacheEngine<>(1, PolicyType.LRU);
        cache.put("a", "old");
        cache.put("a", "new");

        assertEquals("new", cache.get("a").orElseThrow());
        assertEquals(1, cache.size());
        assertEquals(0, cache.stats().evictions());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CacheEngine<String, String>(0, PolicyType.LRU));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CacheEngine<String, String>(-1, PolicyType.LRU));
    }
}
