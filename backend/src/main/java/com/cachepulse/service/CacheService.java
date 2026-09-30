package com.cachepulse.service;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.CacheStats;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.CacheRequest;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class CacheService {
    private volatile CacheEngine<String, String> cache = new CacheEngine<>(4, PolicyType.LRU);

    public CacheEngine<String, String> cache() {
        return cache;
    }

    public CacheStats stats() {
        return cache.stats();
    }

    public PolicyType policy() {
        return cache.getPolicy();
    }

    public void setPolicy(PolicyType policy) {
        cache.setPolicy(policy);
    }

    public synchronized void configure(int capacity, PolicyType policy) {
        cache = new CacheEngine<>(capacity, policy);
    }

    public void clear() {
        cache.clear();
    }

    public void put(String key, String value, Long ttlSeconds) {
        if (ttlSeconds == null) {
            cache.put(key, value);
        } else {
            cache.put(key, value, Duration.ofSeconds(ttlSeconds));
        }
    }

    public CacheRequest get(String key) {
        CacheEngine.GetResult<String> result = cache.getResult(key);
        return new CacheRequest(key, result.value().orElse(null), result.status());
    }

    public boolean remove(String key) {
        return cache.remove(key).isPresent();
    }
}
