package com.cachepulse.dto;

import com.cachepulse.cache.eviction.PolicyType;

public record PolicyRecommendation(
        PolicyType recommendedPolicy,
        String assessment,
        double hitRateDifference,
        String explanation,
        String tradeoff,
        String limitation) {
}
