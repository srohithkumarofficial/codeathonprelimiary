package com.cachepulse.controller;

import com.cachepulse.cache.CacheStats;
import com.cachepulse.service.CacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private final CacheService service;

    public MetricsController(CacheService service) {
        this.service = service;
    }

    @GetMapping
    public CacheStats metrics() {
        return service.stats();
    }
}
