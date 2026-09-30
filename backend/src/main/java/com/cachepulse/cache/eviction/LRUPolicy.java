package com.cachepulse.cache.eviction;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LRUPolicy<K> implements EvictionPolicy<K> {
    private final LinkedHashMap<K, Boolean> order = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public void add(K key) {
        order.put(key, Boolean.TRUE);
    }

    @Override
    public void access(K key) {
        if (order.containsKey(key)) {
            order.get(key);
        }
    }

    @Override
    public void remove(K key) {
        order.remove(key);
    }

    @Override
    public K evict() {
        if (order.isEmpty()) {
            return null;
        }
        Map.Entry<K, Boolean> eldest = order.entrySet().iterator().next();
        K key = eldest.getKey();
        order.remove(key);
        return key;
    }

    @Override
    public void clear() {
        order.clear();
    }
}
