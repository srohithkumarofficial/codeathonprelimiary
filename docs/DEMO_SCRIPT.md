# Demo script

1. Start Spring Boot with `cd backend; .\mvnw.cmd spring-boot:run` and Vite with `cd frontend; npm run dev`. Open the dashboard and point out the backend connection indicator and live cache metrics.
2. Store a synthetic demo key with a short TTL. GET it to show a hit; wait for the TTL and GET again to observe expiration in the live metrics.
3. Change the active eviction policy and capacity, then perform PUT/GET operations. Explain that capacity changes reset the active cache while a policy-only change retains its entries.
4. Open **Workloads** and describe the four deterministic, healthcare-oriented simulated profiles. Emphasize that profiles use synthetic keys and do not represent an organization's production traces or contain patient data.
5. Open **Policy Lab**, choose a profile, set capacity and request count, and run the comparison. Show that the same trace is executed against separate fresh LRU and LFU caches. Review measured hits, misses, evictions, unique keys, repeated accesses, top-key concentration, and 50-request temporal locality.
6. Read the **Observed behavior** explanation. Point out that it is selected from measured policy outcomes and trace characteristics, and applies only to this run.
7. Run the stress test with a modest thread/operation count. Inspect attempted/completed work, errors, final entries, hits, misses, and client-observed duration.
8. Return to **Overview** and clear the active cache. Explain that clear removes entries while cumulative request and lifecycle counters remain.
