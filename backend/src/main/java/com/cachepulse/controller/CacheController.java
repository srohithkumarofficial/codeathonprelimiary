package com.cachepulse.controller;

import com.cachepulse.cache.CacheEngine;
import com.cachepulse.cache.CacheStats;
import com.cachepulse.cache.eviction.PolicyType;
import com.cachepulse.dto.CacheRequest;
import com.cachepulse.dto.PolicyRequest;
import com.cachepulse.dto.PutRequest;
import com.cachepulse.dto.PolicyAnalysisResult;
import com.cachepulse.dto.WorkloadProfile;
import com.cachepulse.service.CacheService;
import com.cachepulse.service.SimulationService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache")
public class CacheController {
    private final CacheService service;
    private final SimulationService simulations;

    public CacheController(CacheService service, SimulationService simulations) {
        this.service = service;
        this.simulations = simulations;
    }

    @GetMapping("/{key}")
    public ResponseEntity<CacheRequest> get(@PathVariable String key) {
        CacheRequest result = service.get(key);
        HttpStatus status = "HIT".equals(result.status()) ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(result);
    }

    @PutMapping("/{key}")
    public ResponseEntity<CacheRequest> put(
            @PathVariable String key,
            @Valid @RequestBody PutRequest request) {
        service.put(key, request.value(), request.ttlSeconds());
        return ResponseEntity.ok(new CacheRequest(key, request.value(), "STORED"));
    }

    @DeleteMapping("/{key}")
    public Map<String, String> delete(@PathVariable String key) {
        String status = service.remove(key) ? "REMOVED" : "MISS";
        return Map.of("status", status, "key", key);
    }

    @DeleteMapping
    public Map<String, String> clear() {
        service.clear();
        return Map.of("status", "CLEARED");
    }

    @GetMapping("/stats")
    public CacheStats stats() {
        return service.stats();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        CacheStats stats = service.stats();
        return Map.of(
                "capacity", stats.capacity(),
                "policy", stats.policy(),
                "workloads", simulations.workloadNames());
    }

    @PostMapping("/policy")
    public Map<String, Object> policy(@Valid @RequestBody PolicyRequest request) {
        service.setPolicy(request.policy());
        return Map.of("policy", service.policy());
    }

    @PostMapping("/simulate")
    public Object simulate(
            @RequestParam(defaultValue = "locality") String workload,
            @RequestParam(defaultValue = "4") int capacity) {
        if (capacity < 1 || capacity > 100_000) {
            throw new IllegalArgumentException("Capacity must be between 1 and 100000");
        }
        return simulations.compare(capacity, workload);
    }

    @GetMapping("/workloads")
    public java.util.List<WorkloadProfile> workloads() {
        return simulations.workloadProfiles();
    }

    @PostMapping("/analyze")
    public PolicyAnalysisResult analyze(
            @RequestParam String workload,
            @RequestParam(defaultValue = "500") int capacity,
            @RequestParam(defaultValue = "10000") int requests) {
        return simulations.analyze(capacity, workload, requests);
    }

    @PostMapping("/configure")
    public Map<String, Object> configure(
            @RequestParam int capacity,
            @RequestParam PolicyType policy) {
        service.configure(capacity, policy);
        return config();
    }

    @PostMapping("/stress")
    public Map<String, Object> stress(
            @RequestParam(defaultValue = "4") int threads,
            @RequestParam(defaultValue = "1000") int operations) {
        long totalOperations = (long) threads * operations;
        if (threads < 1 || threads > 128 || operations < 1 || operations > 100_000
                || totalOperations > 1_000_000) {
            throw new IllegalArgumentException(
                    "threads (1-128), operations (1-100000), and total operations (up to 1000000) required");
        }

        CacheEngine<String, String> cache = service.cache();
        AtomicLong errors = new AtomicLong();
        long attempted = (long) threads * operations;
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int thread = 0; thread < threads; thread++) {
                int workerId = thread;
                jobs.add(executor.submit(() -> runWorker(cache, workerId, operations, errors)));
            }
            for (Future<?> job : jobs) {
                job.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stress run was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Stress run failed", exception);
        } finally {
            executor.shutdownNow();
        }

        return Map.of(
                "attempted", attempted,
                "completed", attempted - errors.get(),
                "errors", errors.get(),
                "finalSize", cache.size(),
                "stats", cache.stats());
    }

    private void runWorker(
            CacheEngine<String, String> cache,
            int workerId,
            int operations,
            AtomicLong errors) {
        for (int operation = 0; operation < operations; operation++) {
            try {
                String key = "stress-" + (operation % 200);
                if ((operation + workerId) % 3 == 0) {
                    cache.put(key, "v" + operation);
                } else {
                    cache.get(key);
                }
            } catch (RuntimeException exception) {
                errors.incrementAndGet();
            }
        }
    }
}
