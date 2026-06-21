package com.typeahead.cache;

import com.typeahead.dto.CacheDebugDTO;
import com.typeahead.dto.SuggestionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DistributedCache — Facade over the consistent hash ring and cache nodes.
 *
 * ============================================================
 * ARCHITECTURE
 * ============================================================
 *
 *   Request (prefix="iph")
 *     ↓
 *   ConsistentHashRing.getNode("iph")
 *     ↓  (hash → ring lookup → "Node-2")
 *   CacheNode["Node-2"].get("iph")
 *     ↓
 *   HIT? → Return cached suggestions
 *   MISS? → Caller queries DB, then calls put()
 *
 * This class coordinates:
 * 1. Routing: Uses ConsistentHashRing to determine which node handles a prefix
 * 2. Read/Write: Delegates to the appropriate CacheNode
 * 3. Invalidation: Clears affected cache entries when data changes
 * 4. Eviction: Periodic cleanup of expired entries
 * 5. Debugging: Exposes routing info for the /cache/debug endpoint
 *
 * ============================================================
 * TRADEOFFS
 * ============================================================
 * - In-memory cache is volatile: data lost on restart (acceptable for cache)
 * - No cross-node replication: a node failure loses its cached data
 *   (the system falls back to DB — correctness is preserved)
 * - In production, replace CacheNode with Redis/Memcached clients
 *   while keeping the same ConsistentHashRing routing logic
 */
@Component
public class DistributedCache {

    private static final Logger log = LoggerFactory.getLogger(DistributedCache.class);

    private final ConsistentHashRing hashRing;
    private final Map<String, CacheNode> nodes;

    /**
     * Initialize the distributed cache with N logical nodes.
     * Each node is placed on the consistent hash ring with virtual nodes.
     *
     * @param ttlSeconds   TTL for cache entries (from application.properties)
     * @param nodeCount    Number of logical cache nodes (default: 4)
     * @param virtualNodes Virtual nodes per physical node (default: 150)
     */
    public DistributedCache(
            @Value("${cache.ttl.seconds:60}") long ttlSeconds,
            @Value("${cache.nodes.count:4}") int nodeCount,
            @Value("${cache.virtual-nodes:150}") int virtualNodes) {

        this.hashRing = new ConsistentHashRing(virtualNodes);
        this.nodes = new ConcurrentHashMap<>();

        long ttlMillis = ttlSeconds * 1000;

        for (int i = 1; i <= nodeCount; i++) {
            String nodeName = "Node-" + i;
            CacheNode node = new CacheNode(nodeName, ttlMillis);
            nodes.put(nodeName, node);
            hashRing.addNode(nodeName);
        }

        log.info("[CACHE] Distributed cache initialized: {} nodes, {} virtual nodes each, TTL={}s",
                nodeCount, virtualNodes, ttlSeconds);
    }

    // ============================================================
    // Core Operations
    // ============================================================

    /**
     * Look up cached suggestions for a prefix.
     *
     * Flow:
     * 1. Hash the prefix → determine target node via consistent hashing
     * 2. Check the target node's local cache
     * 3. Return cached data (hit) or null (miss)
     *
     * On miss, the caller (SuggestionService) queries the DB and calls put().
     *
     * @param prefix The search prefix (lowercase)
     * @return Cached suggestions, or null on miss/expiry
     */
    public List<SuggestionDTO> get(String prefix) {
        String nodeName = hashRing.getNode(prefix);
        if (nodeName == null) return null;

        CacheNode node = nodes.get(nodeName);
        List<SuggestionDTO> result = node.get(prefix);

        log.debug("[CACHE] GET prefix=\"{}\" → node={} hit={}",
                prefix, nodeName, result != null);

        return result;
    }

    /**
     * Store suggestions in the appropriate cache node.
     *
     * @param prefix      The search prefix (lowercase)
     * @param suggestions The suggestions to cache
     */
    public void put(String prefix, List<SuggestionDTO> suggestions) {
        String nodeName = hashRing.getNode(prefix);
        if (nodeName == null) return;

        CacheNode node = nodes.get(nodeName);
        node.put(prefix, suggestions);

        log.debug("[CACHE] PUT prefix=\"{}\" → node={} entries={}",
                prefix, nodeName, suggestions.size());
    }

    /**
     * Invalidate a specific prefix from its cache node.
     *
     * @param prefix The prefix to remove from cache
     */
    public void invalidate(String prefix) {
        String nodeName = hashRing.getNode(prefix);
        if (nodeName == null) return;

        CacheNode node = nodes.get(nodeName);
        node.invalidate(prefix);

        log.debug("[CACHE] INVALIDATE prefix=\"{}\" → node={}", prefix, nodeName);
    }

    /**
     * Invalidate all prefixes of a query that was just updated.
     *
     * When the BatchWriter updates "iphone charger", all these prefixes
     * might have stale cached results:
     *   "i", "ip", "iph", "ipho", "iphon", "iphone", "iphone ", "iphone c", ...
     *
     * We invalidate all of them to ensure the next request fetches fresh data.
     *
     * Tradeoff: This is aggressive invalidation — some prefixes might not
     * even be cached. But it guarantees consistency. The cost is O(L) where
     * L = length of the query string, which is typically < 50 characters.
     *
     * @param query The query that was updated (lowercase)
     */
    public void invalidateByQuery(String query) {
        for (int i = 1; i <= query.length(); i++) {
            String prefix = query.substring(0, i);
            invalidate(prefix);
        }
        log.debug("[CACHE] Invalidated {} prefixes for query=\"{}\"",
                query.length(), query);
    }

    /**
     * Clear all entries from all cache nodes.
     * Used for testing or emergency cache flush.
     */
    public void clearAll() {
        nodes.values().forEach(CacheNode::clear);
        log.info("[CACHE] All nodes cleared");
    }

    // ============================================================
    // Debug & Metrics
    // ============================================================

    /**
     * Get debug information for a prefix — used by /api/cache/debug.
     * Does NOT update hit/miss counters (uses contains() instead of get()).
     *
     * @param prefix The prefix to inspect
     * @return Debug DTO with routing and cache status
     */
    public CacheDebugDTO getDebugInfo(String prefix) {
        String nodeName = hashRing.getNode(prefix);
        int hash = hashRing.getHash(prefix);
        boolean hit = false;

        if (nodeName != null) {
            CacheNode node = nodes.get(nodeName);
            hit = node.contains(prefix);  // No side effects
        }

        return new CacheDebugDTO(prefix, nodeName, hash, hit);
    }

    /**
     * Get per-node metrics for monitoring.
     *
     * @return Map of nodeName → {hits, misses, size, hitRate}
     */
    public Map<String, Map<String, Object>> getNodeMetrics() {
        Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
        for (CacheNode node : nodes.values()) {
            Map<String, Object> nodeMetrics = new LinkedHashMap<>();
            nodeMetrics.put("hits", node.getHits());
            nodeMetrics.put("misses", node.getMisses());
            nodeMetrics.put("size", node.getSize());
            nodeMetrics.put("hitRate", String.format("%.1f%%", node.getHitRate()));
            metrics.put(node.getName(), nodeMetrics);
        }
        return metrics;
    }

    /**
     * Get aggregate cache hit rate across all nodes.
     */
    public double getOverallHitRate() {
        long totalHits = nodes.values().stream().mapToLong(CacheNode::getHits).sum();
        long totalMisses = nodes.values().stream().mapToLong(CacheNode::getMisses).sum();
        long total = totalHits + totalMisses;
        return total > 0 ? (double) totalHits / total * 100 : 0;
    }

    /**
     * Periodic cleanup of expired cache entries across all nodes.
     * Runs every 30 seconds to prevent memory leaks from entries
     * that were cached but never re-accessed (and thus never lazily expired).
     */
    @Scheduled(fixedRate = 30000)
    public void evictExpiredEntries() {
        int totalEvicted = 0;
        for (CacheNode node : nodes.values()) {
            totalEvicted += node.evictExpired();
        }
        if (totalEvicted > 0) {
            log.info("[CACHE] Evicted {} expired entries across all nodes", totalEvicted);
        }
    }

    /** Expose the hash ring for testing */
    ConsistentHashRing getHashRing() {
        return hashRing;
    }
}
