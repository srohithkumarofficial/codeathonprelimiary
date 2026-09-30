package com.cachepulse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cachepulse.cache.CacheStats;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.WorkloadCharacteristics;
import com.cachepulse.service.PolicyInsightEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyInsightEngineTest {
    private final PolicyInsightEngine engine = new PolicyInsightEngine();

    @Test
    void explainsMeasuredLfuAdvantageAndReportsMissesAndEvictions() {
        String insight = engine.explain(characteristics("High", "Medium"), stats(PolicyType.LRU, 0.50, 50, 8),
                stats(PolicyType.LFU, 0.75, 25, 5));

        assertTrue(insight.contains("LFU retained frequently accessed entries"));
        assertTrue(insight.contains("LFU recorded fewer misses (50 LRU, 25 LFU)"));
        assertTrue(insight.contains("8 LRU, 5 LFU"));
    }

    @Test
    void explainsMeasuredLruAdvantageWhenTemporalLocalityIsHigh() {
        String insight = engine.explain(characteristics("Medium", "High"), stats(PolicyType.LRU, 0.80, 20, 3),
                stats(PolicyType.LFU, 0.60, 40, 7));

        assertTrue(insight.contains("LRU achieved a higher measured hit rate"));
    }

    @Test
    void describesCloseMeasuredResultsWithoutDeclaringAWinner() {
        String insight = engine.explain(characteristics("High", "High"), stats(PolicyType.LRU, 0.70, 30, 4),
                stats(PolicyType.LFU, 0.71, 29, 4));

        assertTrue(insight.contains("similar measured results"));
        assertTrue(insight.contains("Both recorded 4 evictions"));
    }

    private WorkloadCharacteristics characteristics(String frequency, String temporal) {
        return new WorkloadCharacteristics(100, 20, 80, 0.8, 0.6, frequency, temporal, "Mixed", List.of(), List.of());
    }

    private CacheStats stats(PolicyType policy, double hitRate, long misses, long evictions) {
        long hits = 100 - misses;
        return new CacheStats(hits, misses, evictions, 0, 10, 10, policy, 100, hitRate, 1 - hitRate);
    }
}
