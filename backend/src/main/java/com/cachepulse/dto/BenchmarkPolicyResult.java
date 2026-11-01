package com.cachepulse.dto;

import com.cachepulse.cache.eviction.PolicyType;
import java.util.List;

public record BenchmarkPolicyResult(
        PolicyType policy,
        int operations,
        int reads,
        int writes,
        long hits,
        long misses,
        double hitRate,
        long evictions,
        long expirations,
        long durationNanos,
        double operationsPerSecond,
        long p50LatencyNanos,
        long p95LatencyNanos,
        long p99LatencyNanos,
        double minOperationsPerSecond,
        double maxOperationsPerSecond,
        long minP95LatencyNanos,
        long maxP95LatencyNanos,
        List<BenchmarkTrialResult> trials) {

    public BenchmarkPolicyResult(
            PolicyType policy,
            int operations,
            int reads,
            int writes,
            long hits,
            long misses,
            double hitRate,
            long evictions,
            long expirations,
            long durationNanos,
            double operationsPerSecond,
            long p50LatencyNanos,
            long p95LatencyNanos,
            long p99LatencyNanos) {
        this(policy, operations, reads, writes, hits, misses, hitRate, evictions, expirations, durationNanos,
                operationsPerSecond, p50LatencyNanos, p95LatencyNanos, p99LatencyNanos, operationsPerSecond,
                operationsPerSecond, p95LatencyNanos, p95LatencyNanos, List.of());
    }
}
