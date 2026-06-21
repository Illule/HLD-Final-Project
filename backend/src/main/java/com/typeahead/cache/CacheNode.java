package com.typeahead.cache;

import com.typeahead.dto.SuggestionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CacheNode — A single logical cache node in the distributed cache cluster.
 *
 * Each CacheNode stores prefix → suggestions mappings with TTL-based expiry.
 * In a real system, each node would be a separate Redis/Memcached instance.
 * Here, we simulate the distributed behavior with in-memory HashMaps.
 *
 * ============================================================
 * DESIGN
 * ============================================================
 * - Storage: ConcurrentHashMap for thread-safe access without global locks
 * - TTL: Lazy expiry on read (no background thread per node)
 *   - On get(): if entry is expired, remove it and return null (miss)
 *   - Tradeoff: expired entries linger until accessed, using memory
 *   - In production Redis, TTL is enforced by the server
 *
 * ============================================================
 * COMPLEXITY
 * ============================================================
 *   get(prefix):  O(1) average (HashMap lookup)
 *   put(prefix):  O(1) average (HashMap insert)
 *   invalidate(): O(1) average (HashMap remove)
 *   clear():      O(n) where n = number of entries
 *
 * Space: O(K × S) where K = number of cached prefixes, S = avg suggestions per prefix
 *
 * ============================================================
 * THREAD SAFETY
 * ============================================================
 * ConcurrentHashMap provides thread-safe reads and writes without synchronization.
 * AtomicLong counters provide lock-free hit/miss tracking.
 * Multiple suggestion requests can read from the same node concurrently.
 */
public class CacheNode {

    private static final Logger log = LoggerFactory.getLogger(CacheNode.class);

    /** Human-readable node name (e.g., "Node-1") */
    private final String name;

    /** Cache storage: prefix → CacheEntry (suggestions + timestamp) */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** Time-to-live for cache entries in milliseconds */
    private final long ttlMillis;

    /** Cache hit counter (thread-safe) */
    private final AtomicLong hits = new AtomicLong(0);

    /** Cache miss counter (thread-safe) */
    private final AtomicLong misses = new AtomicLong(0);

    public CacheNode(String name, long ttlMillis) {
        this.name = name;
        this.ttlMillis = ttlMillis;
        log.info("[CACHE_NODE] Initialized '{}' with TTL={}ms", name, ttlMillis);
    }

    /**
     * Retrieve cached suggestions for a prefix.
     *
     * Implements lazy TTL expiry: if the entry exists but is expired,
     * it is removed and null is returned (cache miss).
     *
     * @param prefix The search prefix (lowercase)
     * @return List of suggestions, or null if not cached or expired
     */
    public List<SuggestionDTO> get(String prefix) {
        CacheEntry entry = cache.get(prefix);

        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        // Check TTL expiry
        if (entry.isExpired(ttlMillis)) {
            cache.remove(prefix);
            misses.incrementAndGet();
            log.debug("[CACHE_NODE] {} expired entry for prefix=\"{}\"", name, prefix);
            return null;
        }

        hits.incrementAndGet();
        return entry.getSuggestions();
    }

    /**
     * Check if a prefix exists in cache without updating hit/miss counters.
     * Used by the debug endpoint to avoid side effects.
     *
     * @param prefix The search prefix
     * @return true if cached and not expired
     */
    public boolean contains(String prefix) {
        CacheEntry entry = cache.get(prefix);
        return entry != null && !entry.isExpired(ttlMillis);
    }

    /**
     * Store suggestions for a prefix in cache.
     * Overwrites any existing entry (effectively resetting the TTL).
     *
     * @param prefix The search prefix (lowercase)
     * @param suggestions The suggestions to cache
     */
    public void put(String prefix, List<SuggestionDTO> suggestions) {
        cache.put(prefix, new CacheEntry(suggestions));
    }

    /**
     * Remove a specific prefix from cache.
     * Called when the BatchWriter updates a query — all related
     * prefixes must be invalidated to prevent stale results.
     */
    public void invalidate(String prefix) {
        cache.remove(prefix);
    }

    /**
     * Clear all entries from this cache node.
     * Used during full cache invalidation or testing.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("[CACHE_NODE] {} cleared {} entries", name, size);
    }

    /**
     * Remove expired entries. Called periodically by DistributedCache
     * to prevent memory leaks from entries that are never re-accessed.
     *
     * @return Number of expired entries removed
     */
    public int evictExpired() {
        int evicted = 0;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().isExpired(ttlMillis)) {
                cache.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("[CACHE_NODE] {} evicted {} expired entries", name, evicted);
        }
        return evicted;
    }

    // ============================================================
    // Metrics
    // ============================================================

    public String getName() { return name; }
    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public int getSize() { return cache.size(); }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total > 0 ? (double) hits.get() / total * 100 : 0;
    }

    // ============================================================
    // Inner Class: CacheEntry
    // ============================================================

    /**
     * A cache entry wrapping the cached suggestions with a creation timestamp.
     * The timestamp is used for TTL-based expiry checks.
     */
    static class CacheEntry {
        private final List<SuggestionDTO> suggestions;
        private final long createdAt;

        CacheEntry(List<SuggestionDTO> suggestions) {
            this.suggestions = suggestions;
            this.createdAt = System.currentTimeMillis();
        }

        List<SuggestionDTO> getSuggestions() {
            return suggestions;
        }

        /**
         * Check if this entry has exceeded its TTL.
         * @param ttlMillis Maximum age in milliseconds
         * @return true if entry is older than ttlMillis
         */
        boolean isExpired(long ttlMillis) {
            return (System.currentTimeMillis() - createdAt) > ttlMillis;
        }
    }
}
