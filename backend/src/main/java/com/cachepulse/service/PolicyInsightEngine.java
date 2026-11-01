package com.cachepulse.service;

import com.cachepulse.cache.CacheStats;
import com.cachepulse.dto.WorkloadCharacteristics;
import com.cachepulse.dto.BenchmarkPolicyResult;
import com.cachepulse.dto.PolicyRecommendation;
import com.cachepulse.cache.eviction.PolicyType;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PolicyInsightEngine {
    public PolicyRecommendation recommend(BenchmarkPolicyResult lru, BenchmarkPolicyResult lfu) {
        double difference = lfu.hitRate() - lru.hitRate();
        int trialCount = Math.max(1, Math.min(lru.trials().size(), lfu.trials().size()));
        String measurements = " Measurements: LRU hit rate " + percent(lru.hitRate()) + ", LFU hit rate "
                + percent(lfu.hitRate()) + "; median throughput over " + trialCount + " trials "
                + whole(lru.operationsPerSecond()) + " vs "
                + whole(lfu.operationsPerSecond()) + " ops/s; median p95 latency "
                + latency(lru.p95LatencyNanos()) + " vs " + latency(lfu.p95LatencyNanos()) + ".";
        String tradeoff = "The recommendation ranks hit rate only. Compare throughput and p95 latency when those metrics matter more for the target workload.";
        if (lru.reads() == 0 || lfu.reads() == 0) {
            return new PolicyRecommendation(null, "INSUFFICIENT", Math.abs(difference),
                    "The trace contained no reads, so it provides no hit-rate evidence for selecting a policy." + measurements,
                    tradeoff,
                    "Throughput and latency are reported as measurements, but this run cannot compare hit behavior.");
        }
        if (Math.abs(difference) < 0.02) {
            return new PolicyRecommendation(null, "SIMILAR", Math.abs(difference),
                    "The measured hit-rate difference is below 2 percentage points; neither policy has a clear hit-rate advantage in this run." + measurements,
                    tradeoff,
                    "Choose based on the target workload and operational constraints. Repeated local JVM timings remain sensitive to runtime and machine load.");
        }

        PolicyType winner = difference > 0 ? PolicyType.LFU : PolicyType.LRU;
        String assessment = Math.abs(difference) >= 0.05 ? "CLEAR_ADVANTAGE" : "MODEST_ADVANTAGE";
        return new PolicyRecommendation(winner, assessment, Math.abs(difference),
                winner + " is recommended for this run because it produced a " + percent(Math.abs(difference))
                        + " higher hit rate than " + (winner == PolicyType.LRU ? "LFU" : "LRU") + "." + measurements,
                tradeoff,
                "This recommendation uses hit rate from one deterministic synthetic trace. It is not a universal policy ranking; median throughput and latency summarize repeated local JVM trials and can vary with machine load.");
    }

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

    private String whole(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private String latency(long nanos) {
        if (nanos < 1_000) {
            return nanos + " ns";
        }
        if (nanos < 1_000_000) {
            return String.format(Locale.ROOT, "%.2f µs", nanos / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
    }
}
