package com.cachepulse.dto;

import com.cachepulse.cache.CacheStats;
import com.cachepulse.cache.eviction.PolicyType;

public record SimulationResult(PolicyType policy, String workload, CacheStats stats) {
}
