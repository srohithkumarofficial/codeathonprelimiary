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

## Limitations

The API stores strings and runs as one process; state is lost on restart. Expired, never-read entries can occupy capacity until overwritten or explicitly removed. The global cache lock prioritizes correctness over parallel throughput. Analysis and stress requests are synchronous and bounded to 100,000 requests or 1,000,000 stress operations per call. Workload profiles are synthetic education/demo data, not patient information or production traces.
