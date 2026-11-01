package com.cachepulse.dto;

public record BenchmarkTrialResult(
        int trial,
        long durationNanos,
        double operationsPerSecond,
        long p50LatencyNanos,
        long p95LatencyNanos,
        long p99LatencyNanos,
        long hits,
        long misses,
        long evictions,
        long expirations) {
}
