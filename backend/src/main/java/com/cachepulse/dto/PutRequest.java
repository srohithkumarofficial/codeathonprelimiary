package com.cachepulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PutRequest(@NotBlank String value, @Positive Long ttlSeconds) {
}
