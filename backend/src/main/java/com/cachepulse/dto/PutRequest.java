package com.cachepulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PutRequest(
        @NotBlank @Size(max = 65_536, message = "Value must be at most 65536 characters") String value,
        @Positive Long ttlSeconds) {
}
