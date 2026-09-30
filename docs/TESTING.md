# Testing

Run backend tests and packaging from `backend/` in PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The Maven Wrapper pins Maven 3.9.16. A JDK 17+ must be installed; set `JAVA_HOME` if your shell does not already expose it. The JUnit suite covers cache operations, LRU/LFU behavior, TTL, metrics, concurrency, legacy simulations, and healthcare-profile analysis.

Build the frontend from `frontend/`:

```powershell
npm install
npm run build
```

Manual API checks can use the examples in [API](API.md) after starting the backend. Verify `/api/cache/stats`, `/api/cache/workloads`, each `/api/cache/analyze` profile, active-cache PUT/GET/TTL and policy changes, and `/api/cache/stress`. Analysis results and stress totals should come from backend responses, not UI fixtures.
