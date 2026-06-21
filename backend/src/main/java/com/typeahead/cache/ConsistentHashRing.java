package com.typeahead.cache;

import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ConsistentHashRing — Distributed cache routing via consistent hashing.
 *
 * ============================================================
 * DESIGN RATIONALE
 * ============================================================
 *
 * Problem: How to distribute cache keys across N nodes such that
 *   adding/removing a node remaps only ~1/N keys (not all keys)?
 *
 * Solution: Consistent hashing maps both nodes and keys onto a
 *   circular hash ring. Each key is assigned to the nearest
 *   clockwise node.
 *
 * Virtual Nodes:
 *   Without virtual nodes, 4 physical nodes create only 4 points
 *   on the ring → highly uneven distribution. With 150 virtual
 *   nodes per physical node, we get 600 evenly-spaced points,
 *   ensuring near-uniform key distribution (~25% per node ±2%).
 *
 * ============================================================
 * COMPLEXITY
 * ============================================================
 *   getNode(key):   O(log V) where V = total virtual nodes (TreeMap.ceilingEntry)
 *   addNode(node):  O(Vn log V) where Vn = virtual nodes per physical node
 *   removeNode(node): O(Vn log V)
 *
 * Space: O(V) for the ring + O(N × Vn) for node position tracking
 *
 * ============================================================
 * HASH FUNCTION
 * ============================================================
 * Uses Google Guava's MurmurHash3 (128-bit variant):
 *   - Non-cryptographic, optimized for speed
 *   - Excellent avalanche properties (small input changes → large hash changes)
 *   - Uniform distribution across the int range
 *   - ~10x faster than SHA-256 for our use case
 *
 * ============================================================
 * FAILURE SCENARIOS
 * ============================================================
 * - Node failure: removeNode() remaps only ~1/N keys to adjacent nodes
 * - Node addition: addNode() takes ~1/N keys from existing nodes
 * - Empty ring: getNode() returns null (caller must handle)
 */
public class ConsistentHashRing {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);

    /**
     * The hash ring: maps hash values to physical node names.
     * TreeMap provides O(log n) ceiling/floor lookups for clockwise routing.
     */
    private final TreeMap<Integer, String> ring = new TreeMap<>();

    /**
     * Tracks which hash positions belong to each physical node.
     * Used by removeNode() to efficiently remove all virtual node entries.
     */
    private final Map<String, List<Integer>> nodePositions = new HashMap<>();

    /** Number of virtual nodes per physical node */
    private final int virtualNodeCount;

    /**
     * @param virtualNodeCount Virtual nodes per physical node.
     *   Recommended: 100-200 for 3-10 node clusters.
     *   More = better distribution but slightly more memory.
     */
    public ConsistentHashRing(int virtualNodeCount) {
        this.virtualNodeCount = virtualNodeCount;
        log.info("[HASH_RING] Initialized with {} virtual nodes per physical node", virtualNodeCount);
    }

    /**
     * Add a physical node to the ring with its virtual nodes.
     *
     * Each virtual node is placed at hash(nodeName + "-VN" + i).
     * The "-VN" suffix ensures different positions for each virtual node.
     *
     * @param nodeName The physical node identifier (e.g., "Node-1")
     */
    public synchronized void addNode(String nodeName) {
        List<Integer> positions = new ArrayList<>(virtualNodeCount);

        for (int i = 0; i < virtualNodeCount; i++) {
            int hash = hash(nodeName + "-VN" + i);
            ring.put(hash, nodeName);
            positions.add(hash);
        }

        nodePositions.put(nodeName, positions);
        log.info("[HASH_RING] Added node '{}' with {} virtual nodes. Total ring size: {}",
                nodeName, virtualNodeCount, ring.size());
    }

    /**
     * Remove a physical node and all its virtual nodes from the ring.
     * Keys that were mapped to this node will be remapped to the next
     * clockwise node — affecting only ~1/N of all keys.
     *
     * @param nodeName The physical node to remove
     */
    public synchronized void removeNode(String nodeName) {
        List<Integer> positions = nodePositions.remove(nodeName);
        if (positions != null) {
            positions.forEach(ring::remove);
            log.info("[HASH_RING] Removed node '{}'. {} virtual nodes removed. Ring size: {}",
                    nodeName, positions.size(), ring.size());
        } else {
            log.warn("[HASH_RING] Attempted to remove unknown node: {}", nodeName);
        }
    }

    /**
     * Determine which cache node should handle a given key.
     *
     * Algorithm:
     * 1. Hash the key → integer position on the ring
     * 2. Find the nearest clockwise node via TreeMap.ceilingEntry()
     * 3. If no clockwise node exists (key hash > max), wrap around to first node
     *
     * Time complexity: O(log V) where V = total virtual nodes
     *
     * @param key The cache key (typically a search prefix like "iph")
     * @return The physical node name, or null if the ring is empty
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            log.warn("[HASH_RING] Ring is empty — no nodes available");
            return null;
        }

        int hash = hash(key);
        // Find the nearest clockwise node
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            // Wrap around to the first node on the ring
            entry = ring.firstEntry();
        }

        log.debug("[HASH_RING] Key \"{}\" → hash={} → node={}",
                key, hash, entry.getValue());

        return entry.getValue();
    }

    /**
     * Get the raw hash value for a key.
     * Exposed for debugging/metrics (CacheDebugDTO).
     */
    public int getHash(String key) {
        return hash(key);
    }

    /**
     * Get the number of physical nodes currently on the ring.
     */
    public int getNodeCount() {
        return nodePositions.size();
    }

    /**
     * Get the total number of virtual nodes on the ring.
     */
    public int getRingSize() {
        return ring.size();
    }

    /**
     * Get all physical node names.
     */
    public Set<String> getNodeNames() {
        return Collections.unmodifiableSet(nodePositions.keySet());
    }

    /**
     * Compute MurmurHash3 for a given key string.
     *
     * MurmurHash3 properties:
     * - 128-bit output, truncated to 32-bit int for TreeMap key
     * - Non-cryptographic: optimized for speed (~3 GB/s)
     * - Excellent avalanche: flipping 1 input bit changes ~50% of output bits
     * - Uniform distribution: keys map evenly across the int range
     *
     * We use the full int range (including negatives) since TreeMap
     * sorts by natural ordering of Integer, which handles negatives correctly.
     */
    private int hash(String key) {
        return Hashing.murmur3_128()
                .hashString(key, StandardCharsets.UTF_8)
                .asInt();
    }
}
