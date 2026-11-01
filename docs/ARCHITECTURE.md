# Architecture

The React dashboard talks to Spring Boot through the central frontend API service. `CacheService` owns the active `CacheEngine<String,String>`, and controllers map HTTP requests and DTOs to service operations. The cache engine owns entries, expiry checks, capacity and maintained counters; eviction metadata is delegated to `EvictionPolicy`.

LRU uses Java's access-order linked hash map. LFU tracks key frequency and ordered frequency buckets; equal-frequency victims are least recently touched. TTL is stored per entry and is checked on cache operations, independent of eviction strategy. No background expiry scan is performed.

A single `ReentrantLock` protects compound cache state and counters. Stats endpoints read an immutable snapshot made from maintained counters and fixed metadata; they do not scan entries or policy structures. Policy Lab creates two independent temporary engines, runs the same generated trace through each, and returns actual measurements.

The healthcare workload lab is deterministic and stateless. `SimulationService` generates synthetic access keys from one of four profiles, analyzes frequency concentration and short-window recency from that trace, then executes it against LRU and LFU.

`BenchmarkService` generates a bounded seeded sequence of reads and writes for uniform, Zipfian, sequential-scan, bursty, read-heavy, or mixed patterns. It fingerprints the ordered sequence, then runs it through independent caches with the same capacity, TTL, and virtual operation clock. It measures cache operations separately from trace generation and returns per-policy metrics. `PolicyInsightEngine` turns the measured hit-rate comparison into a deterministic recommendation and withholds a winner when evidence is insufficient or the gap is small. Throughput and latency are descriptive measurements from a single local JVM run. The separate concurrency experiment uses the active shared cache and is not presented as a serial policy comparison.

All healthcare-themed and benchmark keys are synthetic. The application uses no patient data, external AI service, or production traces.
