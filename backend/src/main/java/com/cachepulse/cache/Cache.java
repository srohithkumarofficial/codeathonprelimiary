package com.cachepulse.cache;

import java.time.Duration;
import java.util.Optional;

public interface Cache<K, V> {
    void put(K key, V value);

    void put(K key, V value, Duration ttl);

    Optional<V> get(K key);

    Optional<V> remove(K key);

    void clear();

    int size();
}
