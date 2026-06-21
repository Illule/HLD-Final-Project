package com.typeahead.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SearchBuffer — Thread-safe buffer for incoming search queries.
 *
 * ============================================================
 * PURPOSE
 * ============================================================
 *
 * Instead of writing every search to the database immediately (1000 searches
 * = 1000 DB writes), searches are buffered here and periodically flushed
 * by the BatchWriter in aggregated batches (1000 searches ≈ 50 DB writes).
 *
 * ============================================================
 * DESIGN
 * ============================================================
 *
 *   SearchService.submitSearch("iphone")
 *     ↓
 *   SearchBuffer.add("iphone")
 *     ↓
 *   ConcurrentLinkedQueue ["iphone", "java", "iphone", "iphone"]
 *     ↓  (every 5 seconds or when size >= 100)
 *   BatchWriter.flush()
 *     ↓
 *   SearchBuffer.drainAll()
 *     ↓
 *   Aggregated: {"iphone": 3, "java": 1}
 *     ↓
 *   2 DB writes instead of 4
 *
 * ============================================================
 * THREAD SAFETY
 * ============================================================
 * - ConcurrentLinkedQueue: lock-free, wait-free enqueue/dequeue
 *   Multiple search requests can add() concurrently without contention.
 * - AtomicInteger size: approximate count for threshold checking.
 *   May be slightly inaccurate under extreme concurrency (acceptable).
 *
 * ============================================================
 * CRASH SCENARIO
 * ============================================================
 * If the application crashes before flush, buffered queries are LOST.
 * Maximum data loss: up to 5 seconds of searches (or up to 100 searches).
 *
 * Mitigation (not implemented): Write-Ahead Log (WAL)
 *   - Before enqueue, append to a file: search_buffer.wal
 *   - On startup, replay WAL entries
 *   - After successful flush, truncate WAL
 *   - Tradeoff: adds disk I/O per search (~0.1ms) but prevents data loss
 */
@Component
public class SearchBuffer {

    private static final Logger log = LoggerFactory.getLogger(SearchBuffer.class);

    /**
     * Lock-free queue for incoming search queries.
     * ConcurrentLinkedQueue uses a Michael-Scott algorithm:
     * - Enqueue: CAS on tail pointer → O(1) amortized
     * - Dequeue: CAS on head pointer → O(1) amortized
     * - No blocking, no locks, no contention under high throughput
     */
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    /**
     * Approximate size counter for threshold-based flushing.
     * Uses AtomicInteger for lock-free increment/decrement.
     *
     * Note: This may be slightly out of sync with the actual queue size
     * during concurrent add+drain operations. This is intentional —
     * the threshold check is best-effort, and the periodic flush
     * guarantees eventual processing regardless.
     */
    private final AtomicInteger size = new AtomicInteger(0);

    /**
     * Add a search query to the buffer.
     * Called by SearchService for every search submission.
     *
     * Thread-safe: multiple threads can call add() concurrently.
     * Time complexity: O(1) amortized (CAS-based enqueue)
     *
     * @param query The search query (lowercase, trimmed)
     */
    public void add(String query) {
        queue.offer(query);
        size.incrementAndGet();
        log.debug("[BUFFER] Enqueued query=\"{}\" bufferSize={}", query, size.get());
    }

    /**
     * Drain all queued queries and aggregate duplicate counts.
     *
     * This is called by BatchWriter.flush() to get the aggregated
     * batch for database writing.
     *
     * Example:
     *   Queue: ["iphone", "java", "iphone", "iphone"]
     *   Result: {"iphone": 3, "java": 1}
     *
     * Thread-safety note:
     *   queue.poll() atomically removes items one by one.
     *   If add() is called concurrently during drain, newly added
     *   items may or may not be included in this drain — they'll
     *   be picked up in the next flush cycle. This is acceptable
     *   for eventually-consistent search counts.
     *
     * @return Map of query → count (aggregated)
     */
    public Map<String, Long> drainAll() {
        Map<String, Long> aggregated = new HashMap<>();
        String item;

        while ((item = queue.poll()) != null) {
            aggregated.merge(item, 1L, Long::sum);
            size.decrementAndGet();
        }

        if (!aggregated.isEmpty()) {
            log.debug("[BUFFER] Drained {} unique queries from buffer", aggregated.size());
        }

        return aggregated;
    }

    /**
     * Get the approximate buffer size.
     * Used by BatchWriter to check the flush threshold.
     */
    public int getSize() {
        return size.get();
    }

    /**
     * Check if the buffer is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
