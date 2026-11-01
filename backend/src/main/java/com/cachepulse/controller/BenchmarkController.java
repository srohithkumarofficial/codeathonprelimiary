package com.cachepulse.controller;

import com.cachepulse.dto.BenchmarkRequest;
import com.cachepulse.dto.BenchmarkResult;
import com.cachepulse.service.BenchmarkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {
    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping
    public BenchmarkResult run(@Valid @RequestBody BenchmarkRequest request) {
        return benchmarkService.run(request);
    }
}
