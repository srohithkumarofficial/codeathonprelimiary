package com.cachepulse.cache;

import com.cachepulse.cache.eviction.PolicyType;

public record CacheStats(
        long hits,
        long misses,
        long evictions,
        long expirations,
        int size,
        int capacity,
        PolicyType policy,
        long totalRequests,
        double hitRate,
        double missRate,
        int validEntries,
        int expiredEntries) {

    public CacheStats(
            long hits,
            long misses,
            long evictions,
            long expirations,
            int size,
            int capacity,
            PolicyType policy,
            long totalRequests,
            double hitRate,
            double missRate) {
        this(hits, misses, evictions, expirations, size, capacity, policy, totalRequests, hitRate, missRate, size, 0);
    }
}
