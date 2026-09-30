package com.cachepulse.cache;

import java.time.Instant;

public final class CacheEntry<K, V> {
    private final K key;
    private final V value;
    private final Instant createdAt;
    private final Instant expiresAt;

    public CacheEntry(K key, V value, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.value = value;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
