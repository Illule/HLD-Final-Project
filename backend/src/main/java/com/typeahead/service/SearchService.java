package com.typeahead.service;

import com.typeahead.batch.SearchBuffer;
import com.typeahead.dto.SearchResponseDTO;
import com.typeahead.metrics.PerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SearchService — Handles search query submissions.
 *
 * When a user submits a search, the query is NOT written directly
 * to the database. Instead, it is enqueued in the SearchBuffer
 * for eventual batch processing by the BatchWriter.
 *
 * This decouples the write path from the user-facing request,
 * ensuring consistently low latency regardless of DB load.
 *
 * Flow:
 *   POST /api/search → SearchService.submitSearch()
 *     → buffer.add(query)     [O(1), lock-free]
 *     → return "Searched"     [immediate response]
 *
 * The BatchWriter then:
 *   → Drains buffer every 5s (or when size >= 100)
 *   → Aggregates duplicates
 *   → Single batch DB write
 *   → Invalidates affected cache entries
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final SearchBuffer buffer;
    private final PerformanceTracker metrics;

    public SearchService(SearchBuffer buffer, PerformanceTracker metrics) {
        this.buffer = buffer;
        this.metrics = metrics;
    }

    /**
     * Submit a search query for processing.
     *
     * The query is normalized (lowercase, trimmed) and added to the buffer.
     * The actual database update happens asynchronously via BatchWriter.
     *
     * @param query The raw search query from the user
     * @return Acknowledgment response
     * @throws IllegalArgumentException if query is null or blank
     */
    public SearchResponseDTO submitSearch(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        String normalized = query.toLowerCase().trim();

        // Enqueue for batch processing (O(1), lock-free)
        buffer.add(normalized);
        metrics.recordSearchSubmission();

        log.info("[SEARCH] Submitted query=\"{}\" bufferSize={}", normalized, buffer.getSize());

        return new SearchResponseDTO("Searched");
    }
}
