# Demo script

1. Start Spring Boot with `cd backend; .\mvnw.cmd spring-boot:run` and Vite with `cd frontend; npm run dev`. Open the dashboard and point out the backend connection indicator and live cache metrics.
2. Store a synthetic demo key with a short TTL. GET it to show a hit; wait for the TTL and GET again to observe expiration in the live metrics.
3. Change the active eviction policy and capacity, then perform PUT/GET operations. Explain that capacity changes reset the active cache while a policy-only change retains its entries.
4. Open **Workloads** and describe the four deterministic, healthcare-oriented simulated profiles. Emphasize that profiles use synthetic keys and do not represent an organization's production traces or contain patient data.
5. Open **Automatic Evaluation**. Run a seeded Zipfian workload, then repeat it with the same settings and seed. Point out the matching trace fingerprint and measured LRU/LFU results. Compare hit rate, throughput, p50/p95/p99 latency, evictions, and the recommendation's trade-offs.
6. Change the workload to sequential scan or bursty access and rerun it. Explain how the generated operation pattern and read/write ratio are applied to both fresh caches with the same capacity and virtual TTL clock.
7. Open **Policy Lab**, choose a healthcare-oriented profile, set capacity and request count, and run the comparison. Review measured hits, misses, evictions, unique keys, repeated accesses, top-key concentration, and 50-request temporal locality.
8. Run the stress test with a modest thread/operation count. Inspect run-specific reads, writes, hits, misses, errors, throughput, and final entries. Explain that it exercises the active shared cache and is not the serial LRU/LFU comparison.
9. Return to **Overview** and clear the active cache. Explain that clear removes entries while cumulative request and lifecycle counters remain.
