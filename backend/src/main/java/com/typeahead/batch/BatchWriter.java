package com.typeahead.batch;

import com.typeahead.cache.DistributedCache;
import com.typeahead.entity.SearchQuery;
import com.typeahead.metrics.PerformanceTracker;
import com.typeahead.repository.SearchQueryRepository;
import com.typeahead.service.TrendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * BatchWriter — Periodically flushes the SearchBuffer to the database.
 *
 * ============================================================
 * WHY BATCH WRITES?
 * ============================================================
 *
 * Without batching:
 *   1000 search submissions = 1000 individual DB writes
 *   Each write: connection acquire + query parse + index update + commit
 *   Total: ~1000 × 5ms = 5000ms of DB time
 *
 * With batching (5-second window):
 *   1000 search submissions = ~50 batch flushes × ~20 aggregated queries
 *   But with deduplication: "iphone" searched 100 times → 1 DB write (+100)
 *   Typical reduction: 95%+ fewer DB writes
 *
 * ============================================================
 * FLUSH CONDITIONS
 * ============================================================
 *
 * The buffer is flushed when EITHER condition is met:
 *   1. Time-based: Every 5 seconds (configurable via batch.flush.interval-ms)
 *   2. Size-based: Buffer size >= 100 (configurable via batch.flush.threshold)
 *
 * The time-based flush is the primary mechanism (via @Scheduled).
 * The size-based check happens on each search submission as a safety valve
 * to prevent unbounded buffer growth during traffic spikes.
 *
 * ============================================================
 * FLUSH ALGORITHM
 * ============================================================
 *
 *   1. drainAll() → aggregated Map<query, count>
 *      Example: {"iphone": 47, "java": 12, "python": 8}
 *
 *   2. For each (query, count):
 *      a. Try UPDATE: total_count += count, recent_count += count
 *      b. If UPDATE returns 0 rows (query not in DB):
 *         INSERT new row with count
 *
 *   3. Invalidate cache: all prefixes of each updated query
 *      Example: "iphone" → invalidate "i", "ip", "iph", ..., "iphone"
 *
 *   4. Invalidate trending cache to reflect new data
 *
 * ============================================================
 * CRASH SCENARIO
 * ============================================================
 *
 * If the application crashes between buffer.add() and flush():
 *   - Buffered queries are LOST (in-memory queue)
 *   - Maximum data loss: up to 5 seconds of searches
 *   - Impact: some search counts will be slightly low
 *   - This is acceptable for a search suggestion system
 *     (eventual consistency is sufficient)
 *
 * Optional mitigation: Write-Ahead Log (WAL)
 *   - Append each query to a file before buffering
 *   - On startup, replay the WAL
 *   - After successful flush, truncate the WAL
 *   - Cost: ~0.1ms disk I/O per search
 */
@Component
public class BatchWriter {

    private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

    private final SearchBuffer buffer;
    private final SearchQueryRepository repository;
    private final DistributedCache cache;
    private final TrendingService trendingService;
    private final PerformanceTracker metrics;
    private final int flushThreshold;

    public BatchWriter(SearchBuffer buffer,
                       SearchQueryRepository repository,
                       DistributedCache cache,
                       TrendingService trendingService,
                       PerformanceTracker metrics,
                       @Value("${batch.flush.threshold:100}") int flushThreshold) {
        this.buffer = buffer;
        this.repository = repository;
        this.cache = cache;
        this.trendingService = trendingService;
        this.metrics = metrics;
        this.flushThreshold = flushThreshold;
    }

    /**
     * Scheduled flush: runs every N milliseconds (default: 5000ms).
     * This is the primary flush mechanism — ensures no query sits
     * in the buffer for more than ~5 seconds.
     */
    @Scheduled(fixedRateString = "${batch.flush.interval-ms:5000}")
    public void scheduledFlush() {
        flush();
    }

    /**
     * Threshold check: called when the buffer might be full.
     * This is a safety valve for traffic spikes — prevents
     * unbounded memory growth if search rate exceeds flush rate.
     */
    public void checkAndFlush() {
        if (buffer.getSize() >= flushThreshold) {
            log.info("[BATCH] Threshold reached (buffer={} >= {}), triggering immediate flush",
                    buffer.getSize(), flushThreshold);
            flush();
        }
    }

    /**
     * Drain the buffer, aggregate duplicates, and write to database.
     *
     * This method is synchronized to prevent concurrent flushes
     * from the scheduler and threshold check running simultaneously.
     *
     * @Transactional ensures all writes in a single flush are atomic:
     * either all succeed or all roll back.
     */
    @Transactional
    public synchronized void flush() {
        // 1. Drain and aggregate the buffer
        Map<String, Long> aggregated = buffer.drainAll();
        if (aggregated.isEmpty()) {
            return; // Nothing to flush
        }

        log.info("[BATCH] Flushing {} unique queries (from buffer)", aggregated.size());

        int updates = 0;
        int inserts = 0;

        // 2. Process each aggregated entry
        for (Map.Entry<String, Long> entry : aggregated.entrySet()) {
            String query = entry.getKey();
            long count = entry.getValue();

            // Try to increment existing row
            int rowsUpdated = repository.incrementCount(query, count);

            if (rowsUpdated == 0) {
                // New query — insert with initial count
                SearchQuery newQuery = new SearchQuery();
                newQuery.setQuery(query);
                newQuery.setTotalCount(count);
                newQuery.setRecentCount(count);
                newQuery.setUpdatedAt(LocalDateTime.now());
                repository.save(newQuery);
                inserts++;
            } else {
                updates++;
            }

            // 3. Invalidate cache for all prefixes of this query
            cache.invalidateByQuery(query);

            // Track DB writes
            metrics.recordDbWrite();
        }

        // 4. Invalidate trending cache
        trendingService.invalidateCache();

        // 5. Record batch metrics
        metrics.recordBatchWrite(aggregated.size());

        log.info("[BATCH] Flush complete: {} updates, {} inserts, {} total DB writes",
                updates, inserts, aggregated.size());
    }
}
