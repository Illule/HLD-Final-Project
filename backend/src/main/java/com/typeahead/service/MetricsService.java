package com.typeahead.service;

import com.typeahead.cache.DistributedCache;
import com.typeahead.dto.MetricsDTO;
import com.typeahead.metrics.PerformanceTracker;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MetricsService — Aggregates and exposes system performance metrics.
 *
 * Combines data from PerformanceTracker (latency, hit rates, counters)
 * with per-node cache metrics from DistributedCache.
 *
 * This service is a thin orchestration layer — the actual metric
 * collection happens in PerformanceTracker (atomic counters) and
 * CacheNode (hit/miss tracking).
 */
@Service
public class MetricsService {

    private final PerformanceTracker tracker;
    private final DistributedCache cache;

    public MetricsService(PerformanceTracker tracker, DistributedCache cache) {
        this.tracker = tracker;
        this.cache = cache;
    }

    /**
     * Get aggregated system metrics.
     * @return MetricsDTO with latency, cache, and write statistics
     */
    public MetricsDTO getMetrics() {
        return tracker.getMetrics();
    }

    /**
     * Get per-node cache metrics.
     * @return Map of nodeName → {hits, misses, size, hitRate}
     */
    public Map<String, Map<String, Object>> getCacheNodeMetrics() {
        return cache.getNodeMetrics();
    }

    /**
     * Get the overall cache hit rate across all nodes.
     * @return Hit rate as a percentage (0-100)
     */
    public double getCacheHitRate() {
        return cache.getOverallHitRate();
    }
}
