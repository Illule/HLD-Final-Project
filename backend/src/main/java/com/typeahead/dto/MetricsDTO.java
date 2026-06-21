package com.typeahead.dto;

/**
 * MetricsDTO — Response payload for GET /api/metrics.
 *
 * Aggregates all system performance metrics into a single snapshot.
 * All latency values are in milliseconds.
 *
 * Example JSON:
 * {
 *   "avgLatency": 12,
 *   "p95Latency": 20,
 *   "cacheHitRate": 92,
 *   "dbReads": 250,
 *   "dbWrites": 40,
 *   "batchWrites": 8,
 *   "totalSearches": 1000
 * }
 */
public class MetricsDTO {

    /** Average suggestion request latency in milliseconds */
    private long avgLatency;

    /** 95th percentile latency in milliseconds (95% of requests are faster) */
    private long p95Latency;

    /** Cache hit rate as a percentage (0-100) */
    private long cacheHitRate;

    /** Total number of database read operations */
    private long dbReads;

    /** Total number of database write operations */
    private long dbWrites;

    /** Total number of batch flush operations */
    private long batchWrites;

    /** Total number of search submissions received */
    private long totalSearches;

    public MetricsDTO() {}

    public MetricsDTO(long avgLatency, long p95Latency, long cacheHitRate,
                      long dbReads, long dbWrites, long batchWrites, long totalSearches) {
        this.avgLatency = avgLatency;
        this.p95Latency = p95Latency;
        this.cacheHitRate = cacheHitRate;
        this.dbReads = dbReads;
        this.dbWrites = dbWrites;
        this.batchWrites = batchWrites;
        this.totalSearches = totalSearches;
    }

    // ============================================================
    // Getters and Setters
    // ============================================================

    public long getAvgLatency() {
        return avgLatency;
    }

    public void setAvgLatency(long avgLatency) {
        this.avgLatency = avgLatency;
    }

    public long getP95Latency() {
        return p95Latency;
    }

    public void setP95Latency(long p95Latency) {
        this.p95Latency = p95Latency;
    }

    public long getCacheHitRate() {
        return cacheHitRate;
    }

    public void setCacheHitRate(long cacheHitRate) {
        this.cacheHitRate = cacheHitRate;
    }

    public long getDbReads() {
        return dbReads;
    }

    public void setDbReads(long dbReads) {
        this.dbReads = dbReads;
    }

    public long getDbWrites() {
        return dbWrites;
    }

    public void setDbWrites(long dbWrites) {
        this.dbWrites = dbWrites;
    }

    public long getBatchWrites() {
        return batchWrites;
    }

    public void setBatchWrites(long batchWrites) {
        this.batchWrites = batchWrites;
    }

    public long getTotalSearches() {
        return totalSearches;
    }

    public void setTotalSearches(long totalSearches) {
        this.totalSearches = totalSearches;
    }
}
