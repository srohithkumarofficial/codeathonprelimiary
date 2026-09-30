package com.cachepulse.dto;

import java.util.List;

public record PolicyAnalysisResult(
        String workload,
        String workloadName,
        int capacity,
        List<SimulationResult> comparisons,
        WorkloadCharacteristics characteristics,
        String insight) {
}
