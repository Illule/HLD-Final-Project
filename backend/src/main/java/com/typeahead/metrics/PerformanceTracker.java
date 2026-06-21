package com.typeahead.metrics;

import com.typeahead.dto.MetricsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceTracker — System-wide performance metrics collector.
 *
 * ============================================================
 * METRICS TRACKED
 * ============================================================
 *   - Average latency (ms): mean response time across all suggestion requests
 *   - P95 latency (ms): 95th percentile — 95% of requests are faster than this
 *   - Cache hit rate (%): percentage of requests served from cache
 *   - DB reads: number of database read operations
 *   - DB writes: number of individual database write operations
 *   - Batch writes: number of batch flush operations
 *   - Total searches: number of search submissions received
 *
 * ============================================================
 * P95 CALCULATION
 * ============================================================
 * Uses a circular buffer (ring buffer) of the last 1000 latency samples.
 * On read, the buffer is copied and sorted to find the 95th percentile.
 *
 * Tradeoff:
 *   - Pro: Simple, bounded memory (8KB for 1000 longs), accurate for recent traffic
 *   - Con: Sorting 1000 elements on every /metrics call takes ~0.05ms (negligible)
 *   - Alternative: t-digest or HDR histogram for O(1) percentile lookups
 *     (overkill for our throughput levels)
 *
 * ============================================================
 * THREAD SAFETY
 * ============================================================
 * All counters use AtomicLong for lock-free updates.
 * The latency window uses AtomicInteger for index management.
 *
 * Known approximation: Under extreme concurrency, two threads might
 * write to adjacent indices simultaneously, causing one sample to be
 * slightly delayed. This has negligible impact on P95 accuracy.
 */
@Component
public class PerformanceTracker {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTracker.class);

    /** Size of the circular buffer for P95 calculation */
    private static final int WINDOW_SIZE = 1000;

    // ============================================================
    // Atomic Counters (lock-free, thread-safe)
    // ============================================================

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong dbReads = new AtomicLong(0);
    private final AtomicLong dbWrites = new AtomicLong(0);
    private final AtomicLong batchWrites = new AtomicLong(0);
    private final AtomicLong totalSearches = new AtomicLong(0);

    // ============================================================
    // Circular Buffer for P95 Latency
    // ============================================================

    /**
     * Ring buffer storing the last WINDOW_SIZE latency samples (in nanoseconds).
     * Older samples are overwritten as the index wraps around.
     */
    private final long[] latencyWindow = new long[WINDOW_SIZE];

    /** Current write position in the circular buffer */
    private final AtomicInteger windowIndex = new AtomicInteger(0);

    /** Number of samples written (capped at WINDOW_SIZE) */
    private final AtomicInteger windowCount = new AtomicInteger(0);

    // ============================================================
    // Recording Methods
    // ============================================================

    /**
     * Record a suggestion request latency.
     *
     * @param nanos Latency in nanoseconds (use System.nanoTime() difference)
     */
    public void recordLatency(long nanos) {
        totalRequests.incrementAndGet();
        totalLatencyNanos.addAndGet(nanos);

        // Write to circular buffer (modular index)
        int idx = windowIndex.getAndUpdate(i -> (i + 1) % WINDOW_SIZE);
        latencyWindow[idx] = nanos;
        windowCount.updateAndGet(c -> Math.min(c + 1, WINDOW_SIZE));
    }

    /** Record a cache hit */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /** Record a cache miss */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    /** Record a database read operation */
    public void recordDbRead() {
        dbReads.incrementAndGet();
    }

    /** Record a database write operation */
    public void recordDbWrite() {
        dbWrites.incrementAndGet();
    }

    /**
     * Record a batch flush operation.
     * @param uniqueQueries Number of unique queries in this batch
     */
    public void recordBatchWrite(int uniqueQueries) {
        batchWrites.incrementAndGet();
        log.debug("[METRICS] Batch write: {} unique queries", uniqueQueries);
    }

    /** Record a search submission */
    public void recordSearchSubmission() {
        totalSearches.incrementAndGet();
    }

    // ============================================================
    // Metrics Retrieval
    // ============================================================

    /**
     * Build a complete metrics snapshot.
     *
     * @return MetricsDTO with all current performance data
     */
    public MetricsDTO getMetrics() {
        long requests = totalRequests.get();
        long avgLatencyMs = requests > 0
                ? (totalLatencyNanos.get() / requests) / 1_000_000
                : 0;

        long p95LatencyMs = calculateP95();

        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long cacheHitRate = (hits + misses) > 0
                ? Math.round((double) hits / (hits + misses) * 100)
                : 0;

        return new MetricsDTO(
                avgLatencyMs,
                p95LatencyMs,
                cacheHitRate,
                dbReads.get(),
                dbWrites.get(),
                batchWrites.get(),
                totalSearches.get()
        );
    }

    /**
     * Calculate the 95th percentile latency from the circular buffer.
     *
     * Algorithm:
     * 1. Copy the buffer (snapshot for thread-safety)
     * 2. Sort the copy in ascending order
     * 3. Return the value at index = size * 0.95
     *
     * Time: O(W log W) where W = min(samples, 1000)
     * Space: O(W) for the copy
     *
     * @return P95 latency in milliseconds
     */
    private long calculateP95() {
        int count = windowCount.get();
        if (count == 0) return 0;

        // Create a snapshot of current samples
        long[] snapshot = new long[count];
        System.arraycopy(latencyWindow, 0, snapshot, 0, count);

        // Sort ascending
        Arrays.sort(snapshot);

        // Find the 95th percentile index
        int p95Index = (int) Math.ceil(count * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, count - 1));

        // Convert from nanoseconds to milliseconds
        return snapshot[p95Index] / 1_000_000;
    }

    /**
     * Log a periodic metrics summary.
     * Called by MetricsService or a scheduled job for monitoring.
     */
    public void logSummary() {
        MetricsDTO m = getMetrics();
        log.info("[METRICS] avgLatency={}ms p95={}ms cacheHitRate={}% " +
                        "dbReads={} dbWrites={} batchWrites={} searches={}",
                m.getAvgLatency(), m.getP95Latency(), m.getCacheHitRate(),
                m.getDbReads(), m.getDbWrites(), m.getBatchWrites(), m.getTotalSearches());
    }
}
