package com.typeahead.service;

import com.typeahead.cache.DistributedCache;
import com.typeahead.dto.SuggestionDTO;
import com.typeahead.entity.SearchQuery;
import com.typeahead.metrics.PerformanceTracker;
import com.typeahead.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SuggestionServiceTest — Unit tests for the suggestion service.
 *
 * Tests cache-first read pattern, prefix matching, and edge cases.
 * Uses Mockito to isolate the service from DB and cache dependencies.
 */
@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private SearchQueryRepository repository;

    @Mock
    private DistributedCache cache;

    @Mock
    private PerformanceTracker metrics;

    @InjectMocks
    private SuggestionService service;

    @Test
    @DisplayName("Should return cached suggestions on cache hit")
    void testCacheHit() {
        // Arrange
        List<SuggestionDTO> cachedSuggestions = Arrays.asList(
                new SuggestionDTO("iphone 15", 85000),
                new SuggestionDTO("iphone charger", 60000)
        );
        when(cache.get("iph")).thenReturn(cachedSuggestions);

        // Act
        List<SuggestionDTO> result = service.getSuggestions("iph");

        // Assert
        assertEquals(2, result.size());
        assertEquals("iphone 15", result.get(0).getQuery());
        assertEquals(85000, result.get(0).getCount());

        // Verify DB was NOT called (cache hit path)
        verify(repository, never()).findByPrefix(anyString(), any(PageRequest.class));
        verify(metrics).recordCacheHit();
        verify(metrics, never()).recordCacheMiss();
    }

    @Test
    @DisplayName("Should query DB on cache miss and populate cache")
    void testCacheMiss() {
        // Arrange
        when(cache.get("jav")).thenReturn(null); // Cache miss

        List<SearchQuery> dbResults = Arrays.asList(
                createSearchQuery("java tutorial", 40000),
                createSearchQuery("javascript", 35000)
        );
        when(repository.findByPrefix(eq("jav"), any(PageRequest.class))).thenReturn(dbResults);

        // Act
        List<SuggestionDTO> result = service.getSuggestions("jav");

        // Assert
        assertEquals(2, result.size());
        assertEquals("java tutorial", result.get(0).getQuery());

        // Verify cache was populated
        verify(cache).put(eq("jav"), anyList());
        verify(metrics).recordCacheMiss();
        verify(metrics).recordDbRead();
    }

    @Test
    @DisplayName("Should handle empty prefix gracefully")
    void testEmptyPrefix() {
        List<SuggestionDTO> result = service.getSuggestions("");
        assertTrue(result.isEmpty());

        // Should not touch cache or DB
        verifyNoInteractions(cache, repository);
    }

    @Test
    @DisplayName("Should handle null prefix gracefully")
    void testNullPrefix() {
        List<SuggestionDTO> result = service.getSuggestions(null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(cache, repository);
    }

    @Test
    @DisplayName("Should handle whitespace-only prefix")
    void testWhitespacePrefix() {
        List<SuggestionDTO> result = service.getSuggestions("   ");
        assertTrue(result.isEmpty());
        verifyNoInteractions(cache, repository);
    }

    @Test
    @DisplayName("Should normalize prefix to lowercase")
    void testCaseInsensitive() {
        when(cache.get("iphone")).thenReturn(null);
        when(repository.findByPrefix(eq("iphone"), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        service.getSuggestions("IPHONE");

        // Should query with lowercase prefix
        verify(cache).get("iphone");
        verify(repository).findByPrefix(eq("iphone"), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should return empty list when no matches found")
    void testNoMatches() {
        when(cache.get("xyzabc")).thenReturn(null);
        when(repository.findByPrefix(eq("xyzabc"), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        List<SuggestionDTO> result = service.getSuggestions("xyzabc");

        assertTrue(result.isEmpty());
        verify(cache).put(eq("xyzabc"), eq(Collections.emptyList()));
    }

    @Test
    @DisplayName("Should trim prefix whitespace")
    void testTrimPrefix() {
        when(cache.get("java")).thenReturn(null);
        when(repository.findByPrefix(eq("java"), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        service.getSuggestions("  java  ");

        verify(cache).get("java");
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private SearchQuery createSearchQuery(String query, long count) {
        SearchQuery sq = new SearchQuery();
        sq.setQuery(query);
        sq.setTotalCount(count);
        sq.setRecentCount(count);
        return sq;
    }
}
