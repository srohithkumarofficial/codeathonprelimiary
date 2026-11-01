package com.cachepulse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BenchmarkRequest(
        @NotNull BenchmarkWorkload workloadType,
        @Min(1) @Max(100_000) int operations,
        @Min(1) @Max(100_000) int capacity,
        @Min(1) @Max(100_000) int distinctKeys,
        @Positive @Max(604_800) Long ttlSeconds,
        @Min(0) @Max(100) int readPercentage,
        long seed,
        @Min(1) @Max(20) Integer trials) {

    public BenchmarkRequest(
            BenchmarkWorkload workloadType,
            int operations,
            int capacity,
            int distinctKeys,
            Long ttlSeconds,
            int readPercentage,
            long seed) {
        this(workloadType, operations, capacity, distinctKeys, ttlSeconds, readPercentage, seed, null);
    }
}
