package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.service.PolicyInsightEngine;
import com.cachepulse.service.SimulationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTest {
    private final SimulationService service = new SimulationService(new PolicyInsightEngine());

    @Test
    void runsSameWorkloadAgainstBothPolicies() {
        var results = service.compare(3, "locality");

        assertEquals(2, results.size());
        assertEquals(PolicyType.LRU, results.get(0).policy());
        assertEquals(PolicyType.LFU, results.get(1).policy());
        assertEquals(15, results.get(0).stats().totalRequests());
        assertEquals(15, results.get(1).stats().totalRequests());
        assertTrue(results.get(0).stats().hits() > 0);
        assertTrue(results.get(1).stats().misses() > 0);
    }

    @Test
    void analyzesHealthcareProfileUsingTheSameGeneratedTrace() {
        var result = service.analyze(40, "claims-processing", 2_000);

        assertEquals("Claims Processing", result.workloadName());
        assertEquals(4, service.workloadProfiles().size());
        assertEquals(2, result.comparisons().size());
        assertEquals(2_000, result.characteristics().requests());
        assertEquals(2_000, result.comparisons().get(0).stats().totalRequests());
        assertEquals(2_000, result.comparisons().get(1).stats().totalRequests());
        assertEquals(result.comparisons().get(0).stats().capacity(), result.comparisons().get(1).stats().capacity());
        assertTrue(result.characteristics().uniqueKeys() > 0);
        assertEquals(2_000 - result.characteristics().uniqueKeys(), result.characteristics().repeatedAccesses());
        assertTrue(result.characteristics().topTenPercentAccessShare() > 0);
        assertEquals("High", result.characteristics().frequencyConcentration());
        assertTrue(result.characteristics().temporalLocality() >= 0 && result.characteristics().temporalLocality() <= 1);
        assertEquals("Frequency and temporal", result.characteristics().workloadType());
        assertTrue(result.characteristics().mostAccessedKeys().get(0).key().startsWith("claim-"));
        assertEquals(result.characteristics().uniqueKeys(), result.characteristics().frequencyDistribution().stream()
                .mapToInt(bucket -> bucket.keyCount()).sum());
        assertEquals(2_000, result.characteristics().frequencyDistribution().stream()
                .mapToInt(bucket -> bucket.accessesPerKey() * bucket.keyCount()).sum());
        assertTrue(!result.insight().isBlank());
    }

    @Test
    void rejectsUnknownHealthcareProfile() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(10, "unknown", 100));
    }

    @Test
    void eachHealthcareWorkloadIsDeterministicAndUsesEquivalentTraceCounts() {
        for (String workload : List.of("claims-processing", "member-services", "authorization", "mixed-healthcare")) {
            var first = service.analyze(25, workload, 600);
            var second = service.analyze(25, workload, 600);
            assertEquals(600, first.characteristics().requests());
            assertEquals(first.characteristics(), second.characteristics());
            assertEquals(first.comparisons(), second.comparisons());
            assertTrue(first.comparisons().stream().allMatch(result -> result.stats().totalRequests() == 600));
        }
    }
}
