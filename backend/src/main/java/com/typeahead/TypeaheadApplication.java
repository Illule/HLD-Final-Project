package com.typeahead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Search Typeahead System — Main Application Entry Point
 *
 * This application provides a production-quality autocomplete service with:
 * - Distributed in-memory cache with consistent hashing (4 logical nodes)
 * - Batch write mechanism to reduce DB writes by ~95%
 * - Recency-aware trending search algorithm
 * - Low-latency prefix-based suggestion API
 *
 * Architecture:
 *   React UI → Spring Boot REST API → ConsistentHashRing → CacheNode[1..4] → PostgreSQL/H2
 *   Search submissions → SearchBuffer → BatchWriter → DB + Cache Invalidation
 *
 * @EnableScheduling activates:
 *   - BatchWriter: flushes search buffer every 5 seconds or when threshold reached
 *   - TrendingService: decays recent_count via exponential halving every hour
 *   - CacheNode: periodic expired entry cleanup
 */
@SpringBootApplication
@EnableScheduling
public class TypeaheadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TypeaheadApplication.class, args);
    }
}
