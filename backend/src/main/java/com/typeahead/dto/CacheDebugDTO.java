package com.typeahead.dto;

/**
 * CacheDebugDTO — Response payload for GET /api/cache/debug.
 *
 * Provides transparency into the consistent hashing and cache routing
 * for a given prefix. Useful for debugging and demonstrating how
 * the distributed cache works.
 *
 * Example JSON:
 * {
 *   "prefix": "iph",
 *   "cacheNode": "Node-2",
 *   "hash": 123456,
 *   "hit": true
 * }
 */
public class CacheDebugDTO {

    /** The prefix that was looked up */
    private String prefix;

    /** The cache node determined by consistent hashing */
    private String cacheNode;

    /** The hash value computed for this prefix */
    private int hash;

    /** Whether the prefix was found in cache (true) or not (false) */
    private boolean hit;

    public CacheDebugDTO() {}

    public CacheDebugDTO(String prefix, String cacheNode, int hash, boolean hit) {
        this.prefix = prefix;
        this.cacheNode = cacheNode;
        this.hash = hash;
        this.hit = hit;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getCacheNode() {
        return cacheNode;
    }

    public void setCacheNode(String cacheNode) {
        this.cacheNode = cacheNode;
    }

    public int getHash() {
        return hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    public boolean isHit() {
        return hit;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }
}
