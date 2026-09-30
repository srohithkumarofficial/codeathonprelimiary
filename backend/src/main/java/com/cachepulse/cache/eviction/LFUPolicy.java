package com.cachepulse.cache.eviction;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class LFUPolicy<K> implements EvictionPolicy<K> {
    private final Map<K, Integer> frequencies = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> buckets = new HashMap<>();
    private int minFrequency;

    @Override
    public void add(K key) {
        remove(key);
        frequencies.put(key, 1);
        buckets.computeIfAbsent(1, ignored -> new LinkedHashSet<>()).add(key);
        minFrequency = 1;
    }

    @Override
    public void access(K key) {
        Integer frequency = frequencies.get(key);
        if (frequency == null) {
            return;
        }

        LinkedHashSet<K> currentBucket = buckets.get(frequency);
        currentBucket.remove(key);
        if (currentBucket.isEmpty()) {
            buckets.remove(frequency);
            if (minFrequency == frequency) {
                minFrequency = frequency + 1;
            }
        }

        int nextFrequency = frequency + 1;
        frequencies.put(key, nextFrequency);
        buckets.computeIfAbsent(nextFrequency, ignored -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public void remove(K key) {
        Integer frequency = frequencies.remove(key);
        if (frequency == null) {
            return;
        }

        LinkedHashSet<K> bucket = buckets.get(frequency);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            buckets.remove(frequency);
            if (minFrequency == frequency) {
                minFrequency = buckets.keySet().stream()
                        .mapToInt(Integer::intValue)
                        .min()
                        .orElse(0);
            }
        }
    }

    @Override
    public K evict() {
        LinkedHashSet<K> leastFrequent = buckets.get(minFrequency);
        if (leastFrequent == null || leastFrequent.isEmpty()) {
            return null;
        }

        K victim = leastFrequent.iterator().next();
        remove(victim);
        return victim;
    }

    @Override
    public void clear() {
        frequencies.clear();
        buckets.clear();
        minFrequency = 0;
    }
}
