package com.cachepulse.cache.eviction;

public interface EvictionPolicy<K> {
    void add(K key);

    void access(K key);

    void remove(K key);

    K evict();

    void clear();
}
