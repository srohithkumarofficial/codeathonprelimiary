package com.cachepulse.dto;

import java.util.List;

public record WorkloadCharacteristics(
        int requests,
        int uniqueKeys,
        int repeatedAccesses,
        double topTenPercentAccessShare,
        double temporalLocality,
        String frequencyConcentration,
        String temporalLocalityLevel,
        String workloadType,
        List<KeyFrequency> mostAccessedKeys,
        List<FrequencyBucket> frequencyDistribution) {
}
