package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TTLTest {
    @Test
    void entriesWithoutTtlDoNotExpire() {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LRU);
        cache.put("permanent", "value");

        assertEquals("value", cache.get("permanent").orElseThrow());
        assertEquals(0, cache.stats().expirations());
    }

    @Test
    void expiredGetIsMissAndRemovesEntryFromPolicy() throws InterruptedException {
        CacheEngine<String, String> cache = new CacheEngine<>(2, PolicyType.LRU);
        cache.put("short", "value", Duration.ofMillis(20));
        cache.put("long", "value");
        Thread.sleep(60);

        assertEquals("EXPIRED", cache.getResult("short").status());
        assertEquals(1, cache.size()); // The long-lived entry remains.
        assertEquals(1, cache.stats().misses());
        assertEquals(1, cache.stats().expirations());

        cache.put("replacement", "value");
        assertTrue(cache.get("long").isPresent());
        cache.put("third", "value");
        assertEquals(2, cache.size());
        assertTrue(cache.get("long").isPresent());
    }

    @Test
    void ttlIsPerEntry() throws InterruptedException {
        CacheEngine<String, String> cache = new CacheEngine<>(3, PolicyType.LFU);
        cache.put("short", "value", Duration.ofMillis(20));
        cache.put("long", "value", Duration.ofSeconds(10));
        cache.put("none", "value");
        Thread.sleep(60);

        assertEquals("EXPIRED", cache.getResult("short").status());
        assertTrue(cache.get("long").isPresent());
        assertTrue(cache.get("none").isPresent());
        assertEquals(1, cache.stats().expirations());
    }

    @Test
    void rejectsNonPositiveTtl() {
        CacheEngine<String, String> cache = new CacheEngine<>(1, PolicyType.LRU);
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", "value", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", "value", Duration.ofSeconds(-1)));
    }

    @Test
    void oversizedTtlDoesNotEvictAnotherEntry() {
        CacheEngine<String, String> cache = new CacheEngine<>(1, PolicyType.LRU);
        cache.put("kept", "value");
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("invalid", "value", Duration.ofSeconds(Long.MAX_VALUE)));
        assertTrue(cache.get("kept").isPresent());
    }
}
