package com.typeahead.repository;

import com.typeahead.entity.SearchQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SearchQueryRepository — Data access layer for the search_query table.
 *
 * Extends JpaRepository for standard CRUD + pagination/sorting.
 * Custom queries are defined using JPQL for database portability (H2 + PostgreSQL).
 *
 * Performance Characteristics:
 * - findByPrefix: O(log n) index scan + O(k) result fetch (k = page size, max 10)
 * - incrementCount: O(log n) index lookup + O(1) update
 * - decayRecentCounts: O(n) full table scan — acceptable since it runs once per hour
 *
 * Thread Safety:
 * - All @Modifying queries use @Transactional to ensure atomicity
 * - incrementCount uses SQL arithmetic (total_count = total_count + :count)
 *   which is atomic at the database level — no lost updates
 */
@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {

    /**
     * Find a query by exact match (case-insensitive).
     * Used by BatchWriter to check if a query already exists before inserting.
     */
    Optional<SearchQuery> findByQueryIgnoreCase(String query);

    /**
     * Find queries matching a prefix, sorted by total_count descending.
     * This is the core query powering the autocomplete suggestions.
     *
     * All queries are stored in lowercase, so the prefix parameter
     * should be pre-lowercased by the caller.
     *
     * The Pageable parameter limits results (typically PageRequest.of(0, 10)).
     *
     * SQL equivalent:
     *   SELECT * FROM search_query
     *   WHERE query LIKE 'prefix%'
     *   ORDER BY total_count DESC
     *   LIMIT 10
     */
    @Query("SELECT s FROM SearchQuery s WHERE s.query LIKE CONCAT(:prefix, '%') ORDER BY s.totalCount DESC")
    List<SearchQuery> findByPrefix(@Param("prefix") String prefix, Pageable pageable);

    /**
     * Find the top queries by total_count (descending).
     * Used by TrendingService to get candidate trending queries.
     * Returns more than needed (e.g., 40) so the trending algorithm
     * can re-rank using the recency-aware score formula.
     */
    @Query("SELECT s FROM SearchQuery s ORDER BY s.totalCount DESC")
    List<SearchQuery> findTopByTotalCount(Pageable pageable);

    /**
     * Atomically increment both total_count and recent_count for a query.
     * Uses SQL arithmetic to prevent lost updates under concurrent access.
     *
     * Returns the number of rows updated (0 = query not found, 1 = success).
     * The BatchWriter uses this return value to decide whether to INSERT.
     *
     * @Transactional: Provides its own transaction if called outside one,
     *   or joins the calling transaction (e.g., BatchWriter.flush()).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE SearchQuery s SET s.totalCount = s.totalCount + :count, " +
           "s.recentCount = s.recentCount + :count, " +
           "s.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE s.query = :query")
    int incrementCount(@Param("query") String query, @Param("count") long count);

    /**
     * Exponential decay: halve all recent_counts across the entire table.
     * This is the key mechanism for trending freshness:
     *
     *   Hour 0: recentCount = 1000
     *   Hour 1: recentCount = 500   (50% remaining)
     *   Hour 2: recentCount = 250   (25% remaining)
     *   Hour 4: recentCount = 62    (6.2% remaining)
     *   Hour 8: recentCount = 3     (0.3% remaining)
     *
     * Old trends naturally fade to zero without any explicit cleanup.
     *
     * Cost: O(n) full table update — acceptable since it runs once per hour.
     * In production with millions of rows, consider batch updates or
     * partitioned updates to reduce lock contention.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE SearchQuery s SET s.recentCount = s.recentCount / 2")
    void decayRecentCounts();

    /**
     * Check if a query exists (case-insensitive).
     * Used by the DatasetLoader to avoid duplicate inserts.
     */
    boolean existsByQueryIgnoreCase(String query);
}
