package com.typeahead.service;

import com.typeahead.cache.DistributedCache;
import com.typeahead.dto.SuggestionDTO;
import com.typeahead.entity.SearchQuery;
import com.typeahead.metrics.PerformanceTracker;
import com.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SuggestionService — Core autocomplete logic.
 *
 * Implements the cache-first read pattern:
 *   1. Normalize prefix (lowercase, trim)
 *   2. Check distributed cache → HIT? Return immediately (~1ms)
 *   3. MISS? Query database (O(log n) index scan)
 *   4. Store result in cache for next request
 *   5. Return top-10 suggestions sorted by total_count
 *
 * Performance:
 *   Cache hit:  ~1-2ms (in-memory HashMap lookup)
 *   Cache miss: ~5-15ms (DB query + cache write)
 *   Typical cache hit rate: 80-95% (Zipf distribution of real searches)
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    /** Maximum number of suggestions to return per request */
    private static final int MAX_SUGGESTIONS = 10;

    private final SearchQueryRepository repository;
    private final DistributedCache cache;
    private final PerformanceTracker metrics;

    public SuggestionService(SearchQueryRepository repository,
                             DistributedCache cache,
                             PerformanceTracker metrics) {
        this.repository = repository;
        this.cache = cache;
        this.metrics = metrics;
    }

    /**
     * Get autocomplete suggestions for a prefix.
     *
     * @param prefix The user's current input (e.g., "iph")
     * @return Up to 10 suggestions sorted by popularity (descending)
     */
    public List<SuggestionDTO> getSuggestions(String prefix) {
        long startTime = System.nanoTime();

        // 1. Validate input
        if (prefix == null || prefix.isBlank()) {
            log.debug("[SUGGEST] Empty prefix — returning empty list");
            return Collections.emptyList();
        }

        String normalizedPrefix = prefix.toLowerCase().trim();

        // 2. Check distributed cache (O(log V) hash ring lookup + O(1) node lookup)
        List<SuggestionDTO> cached = cache.get(normalizedPrefix);
        if (cached != null) {
            metrics.recordCacheHit();
            long latency = System.nanoTime() - startTime;
            metrics.recordLatency(latency);
            log.debug("[SUGGEST] CACHE HIT prefix=\"{}\" results={} latency={}µs",
                    normalizedPrefix, cached.size(), latency / 1000);
            return cached;
        }

        // 3. Cache miss — query database
        metrics.recordCacheMiss();
        List<SearchQuery> results = repository.findByPrefix(
                normalizedPrefix,
                PageRequest.of(0, MAX_SUGGESTIONS)
        );
        metrics.recordDbRead();

        // 4. Map entity → DTO
        List<SuggestionDTO> suggestions = results.stream()
                .map(sq -> new SuggestionDTO(sq.getQuery(), sq.getTotalCount()))
                .collect(Collectors.toList());

        // 5. Populate cache for next request
        cache.put(normalizedPrefix, suggestions);

        long latency = System.nanoTime() - startTime;
        metrics.recordLatency(latency);
        log.info("[SUGGEST] CACHE MISS prefix=\"{}\" results={} latency={}µs (DB)",
                normalizedPrefix, suggestions.size(), latency / 1000);

        return suggestions;
    }
}
