package com.cachepulse.service;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.SimulationResult;
import com.cachepulse.dto.PolicyAnalysisResult;
import com.cachepulse.dto.FrequencyBucket;
import com.cachepulse.dto.KeyFrequency;
import com.cachepulse.dto.WorkloadCharacteristics;
import com.cachepulse.dto.WorkloadProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
    private final PolicyInsightEngine insightEngine;

    public SimulationService(PolicyInsightEngine insightEngine) {
        this.insightEngine = insightEngine;
    }

    private static final Map<String, List<String>> WORKLOADS = Map.of(
            "locality", List.of("A", "B", "C", "A", "B", "A", "D", "A", "C", "B", "E", "A", "B", "C", "A"),
            "sequential", List.of("A", "B", "C", "D", "E", "F", "G", "H", "A", "B", "C", "D"),
            "mixed", List.of("A", "B", "A", "C", "D", "A", "E", "B", "F", "A", "C", "G"),
            "burst", List.of("A", "A", "A", "B", "B", "C", "D", "D", "D", "E", "A", "B"));

    private static final List<WorkloadProfile> HEALTHCARE_PROFILES = List.of(
            new WorkloadProfile("claims-processing", "Claims Processing",
                    "Repeated access to provider, member, claim, and rules data with strong locality.",
                    List.of("Hot claim and provider keys", "Frequent rules lookups", "Long tail of cold records")),
            new WorkloadProfile("member-services", "Member Services",
                    "Eligibility, benefits, and claim lookups with a mix of repeat and one-off access.",
                    List.of("Repeated member lookups", "Mixed hot and cold records", "Moderate recency")),
            new WorkloadProfile("authorization", "Authorization",
                    "Authorization checks revisit member-service combinations and shared decision rules.",
                    List.of("High rule reuse", "Repeated service combinations", "Strong frequency concentration")),
            new WorkloadProfile("mixed-healthcare", "Mixed Healthcare",
                    "A deterministic blend of claims, member services, and authorization access patterns.",
                    List.of("Changing locality by phase", "Mixed hot and cold keys", "General-purpose pattern")));

    private static final Map<String, WorkloadProfile> PROFILES_BY_ID = HEALTHCARE_PROFILES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(WorkloadProfile::id, profile -> profile));

    public List<SimulationResult> compare(int capacity, String workload) {
        List<String> trace = WORKLOADS.get(workload);
        if (trace == null) {
            throw new IllegalArgumentException("Unknown workload: " + workload);
        }

        List<SimulationResult> results = new ArrayList<>();
        for (PolicyType policy : PolicyType.values()) {
            CacheEngine<String, String> cache = new CacheEngine<>(capacity, policy);
            for (String key : trace) {
                cache.get(key).orElseGet(() -> {
                    cache.put(key, key);
                    return key;
                });
            }
            results.add(new SimulationResult(policy, workload, cache.stats()));
        }
        return results;
    }

    public Set<String> workloadNames() {
        return WORKLOADS.keySet();
    }

    public List<WorkloadProfile> workloadProfiles() {
        return HEALTHCARE_PROFILES;
    }

    public PolicyAnalysisResult analyze(int capacity, String workload, int requests) {
        if (capacity < 1 || capacity > 100_000) {
            throw new IllegalArgumentException("Capacity must be between 1 and 100000");
        }
        if (requests < 1 || requests > 100_000) {
            throw new IllegalArgumentException("Requests must be between 1 and 100000");
        }
        WorkloadProfile profile = PROFILES_BY_ID.get(workload);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown healthcare workload: " + workload);
        }

        List<String> trace = generateTrace(workload, requests);
        WorkloadCharacteristics characteristics = characterize(trace);
        List<SimulationResult> results = new ArrayList<>();
        for (PolicyType policy : PolicyType.values()) {
            CacheEngine<String, String> cache = new CacheEngine<>(capacity, policy);
            for (String key : trace) {
                if (cache.get(key).isEmpty()) {
                    cache.put(key, key);
                }
            }
            results.add(new SimulationResult(policy, workload, cache.stats()));
        }
        return new PolicyAnalysisResult(
                workload,
                profile.name(),
                capacity,
                List.copyOf(results),
                characteristics,
                insightEngine.explain(
                        characteristics,
                        results.stream().filter(result -> result.policy() == PolicyType.LRU)
                                .findFirst().orElseThrow().stats(),
                        results.stream().filter(result -> result.policy() == PolicyType.LFU)
                                .findFirst().orElseThrow().stats()));
    }

    private List<String> generateTrace(String workload, int requests) {
        int keyCount = Math.max(20, Math.min(2_000, requests / 5));
        int hotCount = Math.max(3, keyCount / 10);
        List<String> trace = new ArrayList<>(requests);
        for (int index = 0; index < requests; index++) {
            int keyIndex;
            int phaseIndex = index % 1_000;
            switch (workload) {
                case "claims-processing" -> keyIndex = phaseIndex % 10 < 8
                        ? (index % 100 < 65 ? 0 : 1)
                        : hotCount + (index * 31) % (keyCount - hotCount);
                case "member-services" -> keyIndex = phaseIndex % 10 < 6
                        ? index % Math.max(hotCount, keyCount / 4)
                        : (index * 17) % keyCount;
                case "authorization" -> keyIndex = phaseIndex % 100 < 85
                        ? index % 3
                        : 3 + (index * 13) % (keyCount - 3);
                case "mixed-healthcare" -> {
                    int phase = (index / 250) % 3;
                    if (phase == 0) {
                        keyIndex = phaseIndex % 10 < 7 ? index % hotCount
                                : hotCount + (index * 19) % (keyCount - hotCount);
                    } else if (phase == 1) {
                        keyIndex = index % 10 < 5 ? index % (keyCount / 3) : (index * 11) % keyCount;
                    } else {
                        keyIndex = phaseIndex % 100 < 80 ? index % 4 : 4 + (index * 23) % (keyCount - 4);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown healthcare workload: " + workload);
            }
            trace.add(domainKey(workload, keyIndex));
        }
        return trace;
    }

    private WorkloadCharacteristics characterize(List<String> trace) {
        Map<String, Integer> frequencies = new HashMap<>();
        Map<String, Integer> lastSeen = new HashMap<>();
        int recentRepeats = 0;
        for (int index = 0; index < trace.size(); index++) {
            String key = trace.get(index);
            frequencies.merge(key, 1, Integer::sum);
            Integer prior = lastSeen.put(key, index);
            if (prior != null && index - prior <= 50) {
                recentRepeats++;
            }
        }

        int topKeyCount = Math.max(1, (int) Math.ceil(frequencies.size() * 0.1));
        int topAccesses = frequencies.values().stream().sorted(java.util.Comparator.reverseOrder())
                .limit(topKeyCount).mapToInt(Integer::intValue).sum();
        double concentration = (double) topAccesses / trace.size();
        double locality = trace.size() < 2 ? 0 : (double) recentRepeats / (trace.size() - 1);
        List<KeyFrequency> topKeys = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> new KeyFrequency(
                        entry.getKey(), entry.getValue(), (double) entry.getValue() / trace.size()))
                .toList();
        Map<Integer, Integer> frequencyBuckets = new TreeMap<>();
        frequencies.values().forEach(count -> frequencyBuckets.merge(count, 1, Integer::sum));
        List<FrequencyBucket> distribution = frequencyBuckets.entrySet().stream()
                .map(entry -> new FrequencyBucket(entry.getKey(), entry.getValue()))
                .toList();
        String frequencyLevel = level(concentration);
        String temporalLevel = level(locality);
        String workloadType = frequencyLevel.equals("High") && temporalLevel.equals("High")
                ? "Frequency and temporal"
                : frequencyLevel.equals("High") ? "Frequency-heavy"
                        : temporalLevel.equals("High") ? "Temporal-heavy" : "Mixed";
        return new WorkloadCharacteristics(
                trace.size(),
                frequencies.size(),
                trace.size() - frequencies.size(),
                concentration,
                locality,
                frequencyLevel,
                temporalLevel,
                workloadType,
                topKeys,
                distribution);
    }

    private String domainKey(String workload, int keyIndex) {
        String[] domains = switch (workload) {
            case "claims-processing" -> new String[] {"claim", "member", "provider", "rules"};
            case "member-services" -> new String[] {"eligibility", "benefits", "claim-history", "member"};
            case "authorization" -> new String[] {"member", "service", "authorization", "rules"};
            case "mixed-healthcare" -> new String[] {"claim", "member", "provider", "authorization"};
            default -> throw new IllegalArgumentException("Unknown healthcare workload: " + workload);
        };
        return domains[keyIndex % domains.length] + "-" + (keyIndex / domains.length);
    }

    private String level(double value) {
        if (value >= 0.70) {
            return "High";
        }
        if (value >= 0.40) {
            return "Medium";
        }
        return "Low";
    }
}
