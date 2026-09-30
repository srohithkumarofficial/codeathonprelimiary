package com.cachepulse.dto;

import java.util.List;

public record WorkloadProfile(
        String id,
        String name,
        String description,
        List<String> behavior) {
}
