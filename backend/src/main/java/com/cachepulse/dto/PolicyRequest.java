package com.cachepulse.dto;

import com.cachepulse.cache.eviction.PolicyType;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(@NotNull PolicyType policy) {
}
