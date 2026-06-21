package com.typeahead.controller;

import com.typeahead.cache.DistributedCache;
import com.typeahead.dto.CacheDebugDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CacheDebugController — REST endpoints for cache inspection.
 *
 * GET /api/cache/debug?prefix={prefix}
 *
 * Returns consistent hashing routing information for a given prefix,
 * including the target cache node, hash value, and hit/miss status.
 *
 * This endpoint is for debugging and demonstrating the distributed
 * cache architecture. In production, it would be behind admin auth.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheDebugController {

    private static final Logger log = LoggerFactory.getLogger(CacheDebugController.class);

    private final DistributedCache cache;

    public CacheDebugController(DistributedCache cache) {
        this.cache = cache;
    }

    /**
     * Get cache debug info for a prefix.
     *
     * @param prefix The search prefix to inspect
     * @return Debug info: {prefix, cacheNode, hash, hit}
     */
    @GetMapping("/debug")
    public ResponseEntity<CacheDebugDTO> debug(
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix) {

        log.debug("[API] GET /cache/debug?prefix={}", prefix);

        CacheDebugDTO debugInfo = cache.getDebugInfo(prefix.toLowerCase().trim());
        return ResponseEntity.ok(debugInfo);
    }

    /**
     * Get per-node cache metrics.
     *
     * @return Map of node metrics: {Node-1: {hits, misses, size, hitRate}, ...}
     */
    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Map<String, Object>>> nodeMetrics() {
        return ResponseEntity.ok(cache.getNodeMetrics());
    }
}
