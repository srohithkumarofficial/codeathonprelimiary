package com.cachepulse.service;

import com.cachepulse.cache.CacheStats;
import com.cachepulse.dto.WorkloadCharacteristics;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PolicyInsightEngine {
    public String explain(WorkloadCharacteristics workload, CacheStats lru, CacheStats lfu) {
        double hitRateGap = lfu.hitRate() - lru.hitRate();
        String observation;
        if (Math.abs(hitRateGap) < 0.02) {
            observation = "LRU and LFU produced similar measured results for this workload. Neither policy showed a substantial advantage under the current access pattern.";
        } else if (hitRateGap > 0
                && "High".equals(workload.frequencyConcentration())) {
            observation = "The workload shows strong frequency locality, with a relatively small set of keys receiving repeated access. LFU retained frequently accessed entries more effectively under this workload.";
        } else if (hitRateGap < 0
                && "High".equals(workload.temporalLocalityLevel())) {
            observation = "The workload shows strong temporal locality, where recently accessed entries are frequently reused. LRU achieved a higher measured hit rate under this workload.";
        } else {
            String observedPolicy = hitRateGap > 0 ? "LFU" : "LRU";
            observation = observedPolicy
                    + " produced a higher measured hit rate under this workload. The outcome is specific to this simulation.";
        }

        long lruMisses = lru.misses();
        long lfuMisses = lfu.misses();
        String missComparison = lruMisses == lfuMisses
                ? "Both policies recorded " + lruMisses + " misses."
                : (lruMisses < lfuMisses ? "LRU" : "LFU") + " recorded fewer misses ("
                        + lruMisses + " LRU, " + lfuMisses + " LFU).";
        String evictionComparison = lru.evictions() == lfu.evictions()
                ? "Both recorded " + lru.evictions() + " evictions."
                : (lru.evictions() < lfu.evictions() ? "LRU" : "LFU") + " recorded fewer evictions ("
                        + lru.evictions() + " LRU, " + lfu.evictions() + " LFU).";
        return observation + " Measured hit rates were " + percent(lru.hitRate()) + " for LRU and "
                + percent(lfu.hitRate()) + " for LFU; miss rates were " + percent(lru.missRate())
                + " and " + percent(lfu.missRate()) + ". " + missComparison + " " + evictionComparison
                + " Frequency locality was " + workload.frequencyConcentration().toLowerCase(Locale.ROOT)
                + " and temporal locality was " + workload.temporalLocalityLevel().toLowerCase(Locale.ROOT) + ".";
    }

    private String percent(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }
}
