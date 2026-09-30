# REST API

Base path: `/api/cache`. JSON errors return `{ "error": "..." }`.

## Active cache

- `GET /{key}` — returns key, value, and `HIT`, `MISS`, or `EXPIRED`. HIT is 200; MISS/EXPIRED is 404. Each lookup updates request metrics.
- `PUT /{key}` — JSON `{ "value": "...", "ttlSeconds": 30 }`; `ttlSeconds` is optional and positive.
- `DELETE /{key}` — remove one key.
- `DELETE /` — clear active entries; cumulative counters remain.
- `GET /stats` — hits, misses, hit rate, miss rate, evictions, expirations, current size, capacity, current policy, and total GET requests.
- `GET /config` — current capacity, policy, and legacy sample workload identifiers.
- `POST /policy` — JSON `{ "policy": "LFU" }` or `{ "policy": "LRU" }`; retains entries while rebuilding policy metadata.
- `POST /configure?capacity=100&policy=LRU` — replace the active cache with the requested capacity and policy. This resets entries and their counters.
- `POST /stress?threads=8&operations=1000` — concurrent GET/PUT work against the active cache. Returns attempted and completed operations, errors, final size, and actual cache stats.

## Policy Lab

- `GET /workloads` — returns the available healthcare-oriented simulated profiles, with descriptions and access-pattern notes.
- `POST /analyze?workload=claims-processing&capacity=500&requests=10000` — deterministically generates one synthetic domain-key trace, characterizes it, then executes the same ordered trace against fresh LRU and LFU caches. Returns both measured `CacheStats`, request/unique/repeated counts, top 10% access share, locality levels, derived workload type, up to five most accessed keys, the exact frequency histogram, and an explanation incorporating measured hit rates, miss rates/counts and evictions. Capacity and request count must each be between 1 and 100,000.
- `POST /simulate?workload=locality&capacity=4` — legacy sample simulation endpoint; runs the same fixed trace against both policies. Workloads: locality, sequential, mixed, burst.

The workload profiles are simulated healthcare-inspired patterns, not production traces. Keys are synthetic and contain no patient data or PHI.

## Metrics endpoint

`GET /api/metrics` returns the same immutable maintained statistics snapshot as `/api/cache/stats`. Metrics retrieval reads counters and snapshot fields; it does not inspect cache data structures.
