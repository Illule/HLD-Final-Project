package com.typeahead.controller;

import com.typeahead.dto.MetricsDTO;
import com.typeahead.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MetricsController — REST endpoint for system performance metrics.
 *
 * GET /api/metrics
 *
 * Returns aggregated performance data including latency percentiles,
 * cache hit rates, and write statistics.
 */
@RestController
@RequestMapping("/api")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Get system metrics.
     *
     * @return MetricsDTO with latency, cache, and write statistics
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsDTO> metrics() {
        log.debug("[API] GET /metrics");
        MetricsDTO metrics = metricsService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get detailed per-node cache metrics.
     *
     * @return Map of node metrics
     */
    @GetMapping("/metrics/cache")
    public ResponseEntity<Map<String, Map<String, Object>>> cacheMetrics() {
        return ResponseEntity.ok(metricsService.getCacheNodeMetrics());
    }
}
