package com.typeahead.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SearchQuery Entity — Core domain model for the typeahead system.
 *
 * Maps to the 'search_query' table and stores every unique search term
 * with its frequency counts.
 *
 * Design Decisions:
 * - SEQUENCE generation: Unlike IDENTITY, allows Hibernate JDBC batch inserts.
 *   With allocationSize=50, Hibernate pre-allocates 50 IDs per DB round-trip,
 *   reducing DatasetLoader time from ~30s to ~3s for 100K records.
 *
 * - Dual counters (totalCount + recentCount):
 *   totalCount = all-time popularity (monotonically increasing)
 *   recentCount = recent popularity (decays via exponential halving every hour)
 *   This enables the trending score formula:
 *     score = 0.7 * normalized(totalCount) + 0.3 * normalized(recentCount)
 *
 * - All queries are stored in lowercase for consistent matching.
 *
 * Indexes:
 *   idx_query: O(log n) exact match + prefix search (LIKE 'prefix%')
 *   idx_total_count: O(log n) top-K retrieval (ORDER BY totalCount DESC)
 */
@Entity
@Table(
    name = "search_query",
    indexes = {
        @Index(name = "idx_query", columnList = "query"),
        @Index(name = "idx_total_count", columnList = "total_count")
    }
)
public class SearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "search_query_seq")
    @SequenceGenerator(
        name = "search_query_seq",
        sequenceName = "search_query_seq",
        allocationSize = 50  // Pre-allocate 50 IDs for batch insert performance
    )
    private Long id;

    /**
     * The search query text, stored in lowercase.
     * UNIQUE constraint prevents duplicate entries; the BatchWriter
     * uses upsert logic (increment existing or insert new).
     */
    @Column(unique = true, nullable = false, length = 500)
    private String query;

    /**
     * All-time total search count.
     * Monotonically increasing — never decremented.
     * Used as the primary signal for suggestion ranking.
     */
    @Column(name = "total_count", nullable = false)
    private Long totalCount = 0L;

    /**
     * Recent search count — decays via exponential halving.
     * Every hour, this value is halved (recentCount = recentCount / 2).
     * After 4 hours, only ~6% of original value remains.
     * After 8 hours, only ~0.4% remains.
     * This naturally lets old trending topics fade away.
     */
    @Column(name = "recent_count", nullable = false)
    private Long recentCount = 0L;

    /**
     * Timestamp of the last count update.
     * Used for freshness tracking and cache invalidation decisions.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ============================================================
    // Constructors
    // ============================================================

    /** Default constructor required by JPA */
    public SearchQuery() {}

    /** Full constructor for programmatic creation (e.g., DatasetLoader) */
    public SearchQuery(String query, Long totalCount, Long recentCount) {
        this.query = query;
        this.totalCount = totalCount;
        this.recentCount = recentCount;
        this.updatedAt = LocalDateTime.now();
    }

    /** Convenience constructor for new queries with initial count */
    public SearchQuery(String query, Long totalCount) {
        this(query, totalCount, totalCount);
    }

    // ============================================================
    // Getters and Setters
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getRecentCount() {
        return recentCount;
    }

    public void setRecentCount(Long recentCount) {
        this.recentCount = recentCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "SearchQuery{" +
                "id=" + id +
                ", query='" + query + '\'' +
                ", totalCount=" + totalCount +
                ", recentCount=" + recentCount +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
