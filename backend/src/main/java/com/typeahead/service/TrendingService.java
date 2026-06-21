package com.typeahead.service;

import com.typeahead.dto.TrendingDTO;
import com.typeahead.entity.SearchQuery;
import com.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TrendingService — Recency-aware trending search algorithm.
 *
 * ============================================================
 * SCORING FORMULA
 * ============================================================
 *
 *   score = 0.7 × normalized(totalCount) + 0.3 × normalized(recentCount)
 *
 * Where:
 *   normalized(x) = x / max(x)  across all candidate queries
 *
 * This produces a score between 0.0 and 1.0 that balances:
 *   - All-time popularity (70% weight): Stable, established queries
 *   - Recent momentum (30% weight): Newly trending queries
 *
 * ============================================================
 * HOW RECENCY WORKS
 * ============================================================
 *
 * 1. TRACKING: Each search increments both total_count and recent_count
 *    via the BatchWriter. recent_count captures "how many times was this
 *    searched recently?"
 *
 * 2. DECAY: A scheduled job halves ALL recent_counts every hour.
 *    This is exponential decay:
 *
 *      Hour 0: recent_count = 1000
 *      Hour 1: recent_count = 500   (50%)
 *      Hour 2: recent_count = 250   (25%)
 *      Hour 4: recent_count = 62    (6.2%)
 *      Hour 8: recent_count = 3     (0.3%)
 *
 *    Old trends naturally fade to zero without explicit cleanup.
 *
 * 3. RANKING EFFECT: A query with high total_count but zero recent_count
 *    gets score = 0.7 × 1.0 + 0.3 × 0.0 = 0.7 (at most).
 *    A newly trending query with moderate total but high recent might get
 *    score = 0.7 × 0.3 + 0.3 × 1.0 = 0.51 — enough to appear in top results.
 *
 * ============================================================
 * CACHE INVALIDATION
 * ============================================================
 *
 * Trending results are cached with a configurable TTL (default: 30s).
 * The cache is force-invalidated after every BatchWriter flush to ensure
 * new search submissions are reflected promptly.
 *
 * ============================================================
 * TRADEOFFS
 * ============================================================
 *
 * Freshness vs. Complexity:
 *   - Simple: Sort by total_count only (no recency, no decay, no scheduling)
 *   - Our approach: Moderate complexity, good freshness via exponential decay
 *   - Complex: Sliding window counters (per-minute buckets), real-time aggregation
 *     More accurate but significantly more infrastructure
 *
 * Decay Granularity:
 *   - 1-hour decay: coarse but simple, sufficient for most use cases
 *   - 5-minute decay: finer but requires more frequent DB updates
 *   - Per-query TTL: most accurate but complex bookkeeping
 */
@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);

    private final SearchQueryRepository repository;

    /** Number of top trending queries to return */
    private final int topCount;

    /** Cache TTL in milliseconds */
    private final long cacheTtlMs;

    /** Cached trending results */
    private volatile List<TrendingDTO> cachedTrending = Collections.emptyList();

    /** Timestamp of last cache refresh */
    private volatile long lastRefreshed = 0;

    public TrendingService(
            SearchQueryRepository repository,
            @Value("${trending.top-count:20}") int topCount,
            @Value("${trending.cache.ttl-seconds:30}") long cacheTtlSeconds) {
        this.repository = repository;
        this.topCount = topCount;
        this.cacheTtlMs = cacheTtlSeconds * 1000;
    }

    /**
     * Get the current trending searches with recency-aware scores.
     *
     * Returns cached results if still fresh (within TTL).
     * Otherwise, recomputes from database.
     *
     * @return List of trending queries sorted by score (descending)
     */
    public List<TrendingDTO> getTrending() {
        long now = System.currentTimeMillis();

        // Return cached if still fresh
        if (!cachedTrending.isEmpty() && (now - lastRefreshed) < cacheTtlMs) {
            log.debug("[TRENDING] Returning cached results (age={}ms)", now - lastRefreshed);
            return cachedTrending;
        }

        // Refresh from database
        return refreshTrending();
    }

    /**
     * Recompute trending scores from database.
     *
     * Synchronized to prevent multiple threads from computing simultaneously
     * (thundering herd protection on cache miss).
     */
    private synchronized List<TrendingDTO> refreshTrending() {
        // Double-check: another thread might have refreshed while we waited
        long now = System.currentTimeMillis();
        if (!cachedTrending.isEmpty() && (now - lastRefreshed) < cacheTtlMs) {
            return cachedTrending;
        }

        // Fetch more candidates than needed for re-ranking
        List<SearchQuery> candidates = repository.findTopByTotalCount(
                PageRequest.of(0, topCount * 2));

        if (candidates.isEmpty()) {
            cachedTrending = Collections.emptyList();
            lastRefreshed = now;
            return cachedTrending;
        }

        // Find max values for normalization
        long maxTotal = candidates.stream()
                .mapToLong(SearchQuery::getTotalCount)
                .max().orElse(1);
        long maxRecent = candidates.stream()
                .mapToLong(SearchQuery::getRecentCount)
                .max().orElse(1);

        // Compute recency-aware score and rank
        cachedTrending = candidates.stream()
                .map(sq -> {
                    double normalizedTotal = (double) sq.getTotalCount() / Math.max(maxTotal, 1);
                    double normalizedRecent = (double) sq.getRecentCount() / Math.max(maxRecent, 1);

                    // Weighted formula: 70% all-time + 30% recency
                    double score = 0.7 * normalizedTotal + 0.3 * normalizedRecent;

                    // Round to 2 decimal places
                    score = Math.round(score * 100.0) / 100.0;

                    return new TrendingDTO(sq.getQuery(), score);
                })
                .sorted(Comparator.comparingDouble(TrendingDTO::getScore).reversed())
                .limit(topCount)
                .collect(Collectors.toList());

        lastRefreshed = System.currentTimeMillis();
        log.info("[TRENDING] Refreshed {} trending queries", cachedTrending.size());

        return cachedTrending;
    }

    /**
     * Exponential decay: halve all recent_counts in the database.
     *
     * Runs every hour (configurable via trending.decay.interval-ms).
     * This causes old trending topics to naturally fade away.
     *
     * Cost: O(n) full table update — acceptable at hourly frequency.
     * In production with millions of rows, consider:
     *   - Batch updates (UPDATE ... WHERE id BETWEEN x AND y)
     *   - Partitioned tables
     *   - Background job with rate limiting
     */
    @Scheduled(fixedRateString = "${trending.decay.interval-ms:3600000}")
    @Transactional
    public void decayRecentCounts() {
        repository.decayRecentCounts();
        invalidateCache();
        log.info("[TRENDING] Exponential decay applied — all recent_counts halved");
    }

    /**
     * Force-invalidate the trending cache.
     * Called by BatchWriter after each flush to ensure fresh results.
     */
    public void invalidateCache() {
        lastRefreshed = 0;
        log.debug("[TRENDING] Cache invalidated");
    }
}
