# Technical documentation

## Cache behavior

The cache is an in-memory bounded key/value store. LRU metadata updates and victim selection use an access-ordered `LinkedHashMap`; LFU bucket changes are expected O(1), while recalculating the minimum frequency when its bucket empties scans remaining distinct frequency buckets. Engine operations take one exclusive lock to protect compound state.

TTL is optional and entry-specific. Non-positive TTL is rejected. An expired entry discovered by GET is removed from storage and policy metadata and increments both misses and expirations. Overwrite or explicit removal of an expired entry also increments expirations. There is no background expiry scan; expiration is observed on access or replacement/removal.

## Metrics

Maintained counters track GET requests, hits, misses, evictions and expirations; current entry count is separately maintained. Stats snapshots read those variables and fixed capacity/current policy under the cache lock without scanning storage. Counters remain cumulative across remove and clear. Hit/miss rates are zero before the first GET. PUT may change size and increase evictions or expirations, but does not contribute to GET request totals.

## Workload Lab methodology

The four healthcare-oriented simulated profiles are Claims Processing, Member Services, Authorization, and Mixed Healthcare. Keys are deterministic synthetic domain keys (for example, `claim-0`, `member-1`, and `rules-2`), not patient data. They model general information workflows and do not reproduce any organization's private production workload. For a chosen profile, capacity, and request count, the service generates one deterministic trace and passes that identical ordered trace to fresh LRU and LFU cache instances. Each trace access is a GET; a miss is followed by a PUT. Returned policy counters therefore reflect actual cache operations.

Trace characterization is calculated from generated keys:

- **Unique keys:** count of distinct generated keys.
- **Repeated accesses:** requests minus unique keys.
- **Top 10% access share:** fraction of all requests belonging to the most frequently accessed 10% of unique keys (rounded up to at least one key).
- **Most accessed keys:** up to five key/count pairs, sorted by descending measured access count and then key.
- **Frequency distribution:** exact histogram mapping each observed accesses-per-key count to the number of keys with that count. Histogram key counts sum to unique keys and weighted access counts sum to requests.
- **Temporal locality:** fraction of accesses whose key appeared within the previous 50 trace positions.
- **Levels and workload type:** Frequency concentration and temporal locality are High at 70% or above, Medium at 40% to below 70%, and Low below 40%. Top-10% share indicates frequency concentration; reuse within the previous 50 trace positions is a temporal locality engineering indicator. Workload type is derived from these measured levels. These descriptors are simple engineering indicators, not scientific predictions.

The policy explanation compares measured LRU/LFU hit rates (a gap below two percentage points is described as similar), miss rates, miss counts, eviction counts, and measured locality levels. It is specific to that deterministic run, not a universal recommendation.

## Automatic benchmark methodology

`BenchmarkService` constructs one seeded operation list before running either cache. It supports uniform key selection, Zipfian rank weighting, sequential key scans, bursty access in 100-operation phases (three hot-key phases followed by a cold-key phase), read-heavy Zipfian access, and configurable mixed reads/writes. Bounds are 100,000 operations, cache entries, and unique keys per request. Values are synthetic strings. The response includes a SHA-256 fingerprint of the operation type, key, and value sequence so users can verify reproducibility without returning a potentially large trace.

Each policy receives a new empty cache with the same capacity and virtual clock. Virtual time advances by one millisecond per logical operation, making TTL outcomes equivalent in time units and reproducible without sleeping. Each policy gets one unmeasured warm-up pass followed by a measured pass. Workload generation and fingerprinting are reported separately. Per-operation latency wraps only the cache call; run duration and throughput include the iteration loop and timer overhead. Percentiles are nearest-rank over the measured operation latencies. Since each policy is measured once in the same JVM, throughput and latency are demonstrations rather than statistically robust performance claims.

The advisor recommends the higher measured hit-rate policy only when the difference is at least two percentage points and the trace contains reads. Otherwise it reports similar results or insufficient evidence. Latency and throughput are reported but do not decide the recommendation because a single wall-clock sample is noisy.

No adaptive policy switching is implemented. The advisor returns a recommendation after a run and does not change the active cache. Switching policies safely would require a sustained observation window, a measured switching cost, and hysteresis to prevent reactions to noisy fluctuations; that is outside the current experiment.

## Limitations

The API stores strings and runs as one process; state is lost on restart. Expired, never-read entries can occupy capacity until accessed or overwritten. The global cache lock prioritizes correctness over parallel throughput. Analysis and benchmark requests are synchronous and bounded to 100,000 operations. Stress requests are synchronous and bounded to 1,000,000 operations per call. Workload profiles are synthetic education/demo data, not patient information or production traces.
