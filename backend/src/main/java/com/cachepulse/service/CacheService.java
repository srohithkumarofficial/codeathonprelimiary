package com.cachepulse.service;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.CacheStats;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.CacheRequest;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CacheService {
    public static final int MAX_CAPACITY = 100_000;
    public static final int MAX_KEY_LENGTH = 256;
    public static final int MAX_VALUE_LENGTH = 65_536;

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private CacheEngine<String, String> cache;
    private long cacheGeneration = 1;

    public CacheService(
            @Value("${cachepulse.cache.capacity:4}") int capacity,
            @Value("${cachepulse.cache.policy:LRU}") PolicyType policy) {
        validateCapacity(capacity);
        this.cache = new CacheEngine<>(capacity, policy);
    }

    public CacheLease acquireCacheLease() {
        lifecycleLock.readLock().lock();
        return new CacheLease(cache, cacheGeneration, lifecycleLock.readLock());
    }

    public CacheStats stats() {
        return withCurrentCache(CacheEngine::stats);
    }

    public PolicyType policy() {
        return withCurrentCache(CacheEngine::getPolicy);
    }

    public void setPolicy(PolicyType policy) {
        lifecycleLock.writeLock().lock();
        try {
            cache.setPolicy(policy);
            cacheGeneration++;
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public void configure(int capacity, PolicyType policy) {
        validateCapacity(capacity);
        lifecycleLock.writeLock().lock();
        try {
            cache = new CacheEngine<>(capacity, policy);
            cacheGeneration++;
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public void clear() {
        withCurrentCache(cache -> {
            cache.clear();
            return null;
        });
    }

    public void put(String key, String value, Long ttlSeconds) {
        validateKey(key);
        if (value == null || value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Value must be at most " + MAX_VALUE_LENGTH + " characters");
        }
        withCurrentCache(cache -> {
            if (ttlSeconds == null) {
                cache.put(key, value);
            } else {
                cache.put(key, value, Duration.ofSeconds(ttlSeconds));
            }
            return null;
        });
    }

    public CacheRequest get(String key) {
        validateKey(key);
        return withCurrentCache(cache -> {
            CacheEngine.GetResult<String> result = cache.getResult(key);
            return new CacheRequest(key, result.value().orElse(null), result.status());
        });
    }

    public boolean remove(String key) {
        validateKey(key);
        return withCurrentCache(cache -> cache.remove(key).isPresent());
    }

    private <T> T withCurrentCache(java.util.function.Function<CacheEngine<String, String>, T> operation) {
        lifecycleLock.readLock().lock();
        try {
            return operation.apply(cache);
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public static final class CacheLease implements AutoCloseable {
        private final CacheEngine<String, String> cache;
        private final long generation;
        private final ReentrantReadWriteLock.ReadLock readLock;
        private boolean closed;

        private CacheLease(
                CacheEngine<String, String> cache,
                long generation,
                ReentrantReadWriteLock.ReadLock readLock) {
            this.cache = cache;
            this.generation = generation;
            this.readLock = readLock;
        }

        public CacheEngine<String, String> cache() {
            return cache;
        }

        public long generation() {
            return generation;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                readLock.unlock();
            }
        }
    }

    private static void validateCapacity(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Capacity must be between 1 and " + MAX_CAPACITY);
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Key must contain 1-" + MAX_KEY_LENGTH + " characters");
        }
    }
}
