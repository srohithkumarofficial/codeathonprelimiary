package com.cachepulse.cache;

import com.cachepulse.cache.concurrency.CacheLock;
import com.cachepulse.cache.eviction.EvictionPolicy;
import com.cachepulse.cache.eviction.LFUPolicy;
import com.cachepulse.cache.eviction.LRUPolicy;
import com.cachepulse.cache.eviction.PolicyType;
import java.time.DateTimeException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class CacheEngine<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Clock clock;
    private final Map<K, CacheEntry<K, V>> entries = new LinkedHashMap<>();
    private final CacheLock lock = new CacheLock();
    private final AtomicInteger currentEntryCount = new AtomicInteger();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder expirations = new LongAdder();

    private EvictionPolicy<K> policy;
    private PolicyType currentPolicy;

    public CacheEngine(int capacity, PolicyType policyType) {
        this(capacity, policyType, Clock.systemUTC());
    }

    public CacheEngine(int capacity, PolicyType policyType, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
        setPolicyInternal(Objects.requireNonNull(policyType, "policyType"));
    }

    public void setPolicy(PolicyType policyType) {
        Objects.requireNonNull(policyType, "policyType");
        lock.lock();
        try {
            setPolicyInternal(policyType);
        } finally {
            lock.unlock();
        }
    }

    public PolicyType getPolicy() {
        lock.lock();
        try {
            return currentPolicy;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, null);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("TTL must be positive");
        }

        lock.lock();
        try {
            Instant now = clock.instant();
            Instant expiresAt;
            try {
                expiresAt = ttl == null ? null : now.plus(ttl);
            } catch (DateTimeException | ArithmeticException exception) {
                throw new IllegalArgumentException("TTL is too large", exception);
            }

            CacheEntry<K, V> existing = entries.get(key);
            if (existing != null && existing.isExpired(now)) {
                entries.remove(key);
                policy.remove(key);
                currentEntryCount.decrementAndGet();
                expirations.increment();
                existing = null;
            }

            if (existing == null && currentEntryCount.get() >= capacity) {
                K victim = policy.evict();
                if (victim != null && entries.remove(victim) != null) {
                    currentEntryCount.decrementAndGet();
                    evictions.increment();
                }
            }

            entries.put(key, new CacheEntry<>(key, value, now, expiresAt));
            if (existing == null) {
                currentEntryCount.incrementAndGet();
            }
            policy.add(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<V> get(K key) {
        return getResult(key).value();
    }

    public GetResult<V> getResult(K key) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            CacheEntry<K, V> entry = entries.get(key);
            totalRequests.increment();
            if (entry == null) {
                misses.increment();
                return new GetResult<>(Optional.empty(), "MISS");
            }

            if (entry.isExpired(clock.instant())) {
                entries.remove(key);
                policy.remove(key);
                currentEntryCount.decrementAndGet();
                expirations.increment();
                misses.increment();
                return new GetResult<>(Optional.empty(), "EXPIRED");
            }

            hits.increment();
            policy.access(key);
            return new GetResult<>(Optional.of(entry.getValue()), "HIT");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<V> remove(K key) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            CacheEntry<K, V> entry = entries.remove(key);
            policy.remove(key);
            if (entry == null) {
                return Optional.empty();
            }
            currentEntryCount.decrementAndGet();
            if (entry.isExpired(clock.instant())) {
                expirations.increment();
                return Optional.empty();
            }
            return Optional.of(entry.getValue());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            entries.clear();
            policy.clear();
            currentEntryCount.set(0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return currentEntryCount.get();
        } finally {
            lock.unlock();
        }
    }

    public CacheStats stats() {
        lock.lock();
        try {
            long hitCount = hits.sum();
            long missCount = misses.sum();
            long requestCount = totalRequests.sum();
            double hitRate = requestCount == 0 ? 0 : (double) hitCount / requestCount;
            double missRate = requestCount == 0 ? 0 : (double) missCount / requestCount;
            Instant now = clock.instant();
            int expiredEntries = 0;
            for (CacheEntry<K, V> entry : entries.values()) {
                if (entry.isExpired(now)) {
                    expiredEntries++;
                }
            }
            return new CacheStats(
                    hitCount,
                    missCount,
                    evictions.sum(),
                    expirations.sum(),
                    currentEntryCount.get(),
                    capacity,
                    currentPolicy,
                    requestCount,
                    hitRate,
                    missRate,
                    currentEntryCount.get() - expiredEntries,
                    expiredEntries);
        } finally {
            lock.unlock();
        }
    }

    private void setPolicyInternal(PolicyType type) {
        currentPolicy = type;
        policy = type == PolicyType.LRU ? new LRUPolicy<>() : new LFUPolicy<>();
        for (K key : entries.keySet()) {
            policy.add(key);
        }
    }

    public record GetResult<T>(Optional<T> value, String status) {
    }

}
