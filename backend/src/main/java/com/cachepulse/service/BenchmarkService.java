package com.cachepulse.service;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.BenchmarkPolicyResult;
import com.cachepulse.dto.BenchmarkRequest;
import com.cachepulse.dto.BenchmarkResult;
import com.cachepulse.dto.BenchmarkTrialResult;
import com.cachepulse.dto.BenchmarkWorkload;
import com.cachepulse.dto.PolicyRecommendation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class BenchmarkService {
    public static final int DEFAULT_TRIALS = 5;
    public static final int MAX_TRIALS = 20;

    private final PolicyInsightEngine insightEngine;

    public BenchmarkService(PolicyInsightEngine insightEngine) {
        this.insightEngine = insightEngine;
    }

    public BenchmarkResult run(BenchmarkRequest request) {
        validate(request);
        BenchmarkRequest configuration = withDefaultTrials(request);
        long generationStarted = System.nanoTime();
        GeneratedWorkload workload = generate(configuration);
        long generationNanos = Math.max(1, System.nanoTime() - generationStarted);

        for (PolicyType policy : PolicyType.values()) {
            execute(configuration, workload.operations(), policy, 0, false);
        }

        Map<PolicyType, List<BenchmarkTrialResult>> measuredTrials = new EnumMap<>(PolicyType.class);
        for (PolicyType policy : PolicyType.values()) {
            measuredTrials.put(policy, new ArrayList<>(configuration.trials()));
        }
        for (int trial = 1; trial <= configuration.trials(); trial++) {
            PolicyType first = trial % 2 == 1 ? PolicyType.LRU : PolicyType.LFU;
            PolicyType second = first == PolicyType.LRU ? PolicyType.LFU : PolicyType.LRU;
            measuredTrials.get(first).add(execute(configuration, workload.operations(), first, trial, true));
            measuredTrials.get(second).add(execute(configuration, workload.operations(), second, trial, true));
        }

        List<BenchmarkPolicyResult> results = List.of(
                aggregate(PolicyType.LRU, measuredTrials.get(PolicyType.LRU), workload.operations()),
                aggregate(PolicyType.LFU, measuredTrials.get(PolicyType.LFU), workload.operations()));
        PolicyRecommendation recommendation = insightEngine.recommend(results.get(0), results.get(1));
        return new BenchmarkResult(configuration, generationNanos, workload.fingerprint(), results, recommendation,
                "Workload generation is reported separately and excluded from execution duration. Each policy uses a fresh cache and virtual clock and receives the same ordered operations. One warm-up pass per policy precedes the measured trials. Per-operation latency measures only the cache call; execution duration and throughput include the operation loop and virtual-clock advancement. Durations and latencies are in nanoseconds. Policy summaries are medians across the configured trials; trial-level timing variability is also reported. Synthetic cache outcomes are reproducible, but wall-clock measurements vary with runtime and machine load.");
    }

    private BenchmarkRequest withDefaultTrials(BenchmarkRequest request) {
        int trials = request.trials() == null ? DEFAULT_TRIALS : request.trials();
        return new BenchmarkRequest(request.workloadType(), request.operations(), request.capacity(),
                request.distinctKeys(), request.ttlSeconds(), request.readPercentage(), request.seed(), trials);
    }

    private GeneratedWorkload generate(BenchmarkRequest request) {
        SplittableRandom random = new SplittableRandom(request.seed());
        List<Operation> operations = new ArrayList<>(request.operations());
        MessageDigest digest = sha256();
        double[] zipfCumulative = zipfCumulative(request.distinctKeys());
        int hotKeyCount = Math.max(1, request.distinctKeys() / 10);
        for (int index = 0; index < request.operations(); index++) {
            boolean read = random.nextInt(100) < request.readPercentage();
            int keyIndex = chooseKey(request.workloadType(), index, request.distinctKeys(), hotKeyCount,
                    zipfCumulative, random);
            OperationType type = read ? OperationType.READ : OperationType.WRITE;
            Operation operation = new Operation(type, "key-" + keyIndex, "value-" + index);
            operations.add(operation);
            digest.update((type.name() + "\0" + operation.key() + "\0" + operation.value() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return new GeneratedWorkload(List.copyOf(operations), HexFormat.of().formatHex(digest.digest()));
    }

    private int chooseKey(BenchmarkWorkload type, int index, int keyCount, int hotKeyCount,
            double[] zipfCumulative, SplittableRandom random) {
        return switch (type) {
            case UNIFORM -> random.nextInt(keyCount);
            case ZIPFIAN, READ_HEAVY -> sampleZipf(zipfCumulative, random);
            case SEQUENTIAL_SCAN -> index % keyCount;
            case BURSTY -> {
                int phase = index / 100;
                if (phase % 4 == 3) {
                    yield hotKeyCount < keyCount ? hotKeyCount + random.nextInt(keyCount - hotKeyCount) : 0;
                }
                int burstKey = (phase * 7) % hotKeyCount;
                yield random.nextInt(100) < 85 ? burstKey : random.nextInt(keyCount);
            }
            case MIXED -> random.nextInt(100) < 70
                    ? sampleZipf(zipfCumulative, random) : random.nextInt(keyCount);
        };
    }

    private double[] zipfCumulative(int keyCount) {
        double[] cumulative = new double[keyCount];
        double total = 0;
        for (int rank = 1; rank <= keyCount; rank++) total += 1.0 / rank;
        double sum = 0;
        for (int rank = 1; rank <= keyCount; rank++) {
            sum += (1.0 / rank) / total;
            cumulative[rank - 1] = sum;
        }
        cumulative[keyCount - 1] = 1.0;
        return cumulative;
    }

    private int sampleZipf(double[] cumulative, SplittableRandom random) {
        double sample = random.nextDouble();
        int low = 0;
        int high = cumulative.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulative[middle] < sample) low = middle + 1;
            else high = middle;
        }
        return Math.min(cumulative.length - 1, low);
    }

    private BenchmarkTrialResult execute(
            BenchmarkRequest request,
            List<Operation> operations,
            PolicyType policy,
            int trial,
            boolean measure) {
        VirtualClock clock = new VirtualClock(Instant.parse("2025-01-01T00:00:00Z"));
        CacheEngine<String, String> cache = new CacheEngine<>(request.capacity(), policy, clock);
        Duration ttl = request.ttlSeconds() == null ? null : Duration.ofSeconds(request.ttlSeconds());
        long[] latencies = measure ? new long[operations.size()] : null;
        long started = measure ? System.nanoTime() : 0;
        for (int index = 0; index < operations.size(); index++) {
            Operation operation = operations.get(index);
            long operationStarted = measure ? System.nanoTime() : 0;
            if (operation.type() == OperationType.READ) {
                cache.get(operation.key());
            } else if (ttl == null) {
                cache.put(operation.key(), operation.value());
            } else {
                cache.put(operation.key(), operation.value(), ttl);
            }
            if (measure) {
                latencies[index] = Math.max(1, System.nanoTime() - operationStarted);
            }
            clock.advanceMillis(1);
        }
        long durationNanos = measure ? Math.max(1, System.nanoTime() - started) : 0;
        if (!measure) return null;

        Arrays.sort(latencies);
        var stats = cache.stats();
        int reads = (int) operations.stream().filter(operation -> operation.type() == OperationType.READ).count();
        return new BenchmarkTrialResult(
                trial,
                durationNanos,
                operations.size() * 1_000_000_000.0 / durationNanos,
                percentile(latencies, 50),
                percentile(latencies, 95),
                percentile(latencies, 99),
                stats.hits(),
                stats.misses(),
                stats.evictions(),
                stats.expirations());
    }

    private BenchmarkPolicyResult aggregate(
            PolicyType policy,
            List<BenchmarkTrialResult> trials,
            List<Operation> operations) {
        BenchmarkTrialResult first = trials.get(0);
        int reads = (int) operations.stream().filter(operation -> operation.type() == OperationType.READ).count();
        int writes = operations.size() - reads;
        return new BenchmarkPolicyResult(
                policy,
                operations.size(),
                reads,
                writes,
                first.hits(),
                first.misses(),
                reads == 0 ? 0 : (double) first.hits() / reads,
                first.evictions(),
                first.expirations(),
                medianLong(trials.stream().map(BenchmarkTrialResult::durationNanos).toList()),
                medianDouble(trials.stream().map(BenchmarkTrialResult::operationsPerSecond).toList()),
                medianLong(trials.stream().map(BenchmarkTrialResult::p50LatencyNanos).toList()),
                medianLong(trials.stream().map(BenchmarkTrialResult::p95LatencyNanos).toList()),
                medianLong(trials.stream().map(BenchmarkTrialResult::p99LatencyNanos).toList()),
                trials.stream().mapToDouble(BenchmarkTrialResult::operationsPerSecond).min().orElseThrow(),
                trials.stream().mapToDouble(BenchmarkTrialResult::operationsPerSecond).max().orElseThrow(),
                trials.stream().mapToLong(BenchmarkTrialResult::p95LatencyNanos).min().orElseThrow(),
                trials.stream().mapToLong(BenchmarkTrialResult::p95LatencyNanos).max().orElseThrow(),
                List.copyOf(trials));
    }

    private long medianLong(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) return sorted[middle];
        return Math.round(sorted[middle - 1] / 2.0 + sorted[middle] / 2.0);
    }

    private double medianDouble(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) return sorted[middle];
        return (sorted[middle - 1] + sorted[middle]) / 2;
    }

    private long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private void validate(BenchmarkRequest request) {
        if (request == null || request.workloadType() == null) {
            throw new IllegalArgumentException("A workload type is required");
        }
        if (request.operations() < 1 || request.operations() > 100_000) {
            throw new IllegalArgumentException("Operations must be between 1 and 100000");
        }
        if (request.capacity() < 1 || request.capacity() > 100_000) {
            throw new IllegalArgumentException("Capacity must be between 1 and 100000");
        }
        if (request.distinctKeys() < 1 || request.distinctKeys() > 100_000) {
            throw new IllegalArgumentException("Distinct keys must be between 1 and 100000");
        }
        if (request.readPercentage() < 0 || request.readPercentage() > 100) {
            throw new IllegalArgumentException("Read percentage must be between 0 and 100");
        }
        if (request.workloadType() == BenchmarkWorkload.READ_HEAVY && request.readPercentage() < 90) {
            throw new IllegalArgumentException("Read-heavy workloads require at least 90 percent reads");
        }
        if (request.ttlSeconds() != null && (request.ttlSeconds() < 1 || request.ttlSeconds() > 604_800)) {
            throw new IllegalArgumentException("TTL must be between 1 and 604800 seconds");
        }
        if (request.trials() != null && (request.trials() < 1 || request.trials() > MAX_TRIALS)) {
            throw new IllegalArgumentException("Trials must be between 1 and " + MAX_TRIALS);
        }
    }

    private enum OperationType { READ, WRITE }

    private record Operation(OperationType type, String key, String value) { }

    private record GeneratedWorkload(List<Operation> operations, String fingerprint) { }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class VirtualClock extends Clock {
        private final AtomicReference<Instant> instant;

        private VirtualClock(Instant start) {
            this.instant = new AtomicReference<>(start);
        }

        private void advanceMillis(long millis) {
            instant.updateAndGet(current -> current.plusMillis(millis));
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }
}
