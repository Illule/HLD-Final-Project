package com.typeahead.service;

import com.typeahead.batch.SearchBuffer;
import com.typeahead.batch.BatchWriter;
import com.typeahead.cache.DistributedCache;
import com.typeahead.metrics.PerformanceTracker;
import com.typeahead.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BatchWriterTest — Unit tests for the batch write mechanism.
 *
 * Tests buffer aggregation, flush conditions, and DB interaction.
 */
@ExtendWith(MockitoExtension.class)
class BatchWriterTest {

    @Mock
    private SearchQueryRepository repository;

    @Mock
    private DistributedCache cache;

    @Mock
    private TrendingService trendingService;

    @Mock
    private PerformanceTracker metrics;

    private SearchBuffer buffer;
    private BatchWriter batchWriter;

    @BeforeEach
    void setUp() {
        buffer = new SearchBuffer(); // Use real buffer for integration testing
        batchWriter = new BatchWriter(buffer, repository, cache, trendingService, metrics, 100);
    }

    @Test
    @DisplayName("Should aggregate duplicate queries before writing")
    void testAggregation() {
        // Add queries with duplicates
        buffer.add("iphone");
        buffer.add("iphone");
        buffer.add("iphone");
        buffer.add("java");
        buffer.add("java");

        // Verify buffer has all items
        assertEquals(5, buffer.getSize());

        // Drain and verify aggregation
        Map<String, Long> aggregated = buffer.drainAll();
        assertEquals(2, aggregated.size(), "Should have 2 unique queries");
        assertEquals(3L, aggregated.get("iphone"), "iphone should be aggregated to 3");
        assertEquals(2L, aggregated.get("java"), "java should be aggregated to 2");

        // Buffer should be empty after drain
        assertEquals(0, buffer.getSize());
    }

    @Test
    @DisplayName("Should flush buffer and update existing queries")
    void testFlushExistingQueries() {
        buffer.add("iphone");
        buffer.add("iphone");

        // Simulate: query exists in DB (incrementCount returns 1 row updated)
        when(repository.incrementCount(eq("iphone"), eq(2L))).thenReturn(1);

        batchWriter.flush();

        // Verify DB update was called (not insert)
        verify(repository).incrementCount("iphone", 2L);
        verify(repository, never()).save(any());

        // Verify cache was invalidated for all prefixes of "iphone"
        verify(cache).invalidateByQuery("iphone");
        verify(trendingService).invalidateCache();
        verify(metrics).recordDbWrite();
    }

    @Test
    @DisplayName("Should insert new queries when they don't exist in DB")
    void testFlushNewQueries() {
        buffer.add("newquery");

        // Simulate: query doesn't exist (incrementCount returns 0)
        when(repository.incrementCount(eq("newquery"), eq(1L))).thenReturn(0);

        batchWriter.flush();

        // Verify INSERT was called
        verify(repository).save(any());
        verify(cache).invalidateByQuery("newquery");
    }

    @Test
    @DisplayName("Should not flush when buffer is empty")
    void testFlushEmptyBuffer() {
        batchWriter.flush();

        // Nothing should happen
        verifyNoInteractions(repository);
        verifyNoInteractions(cache);
        verifyNoInteractions(trendingService);
    }

    @Test
    @DisplayName("Should demonstrate write reduction (batching benefit)")
    void testWriteReduction() {
        // Simulate 1000 searches for 50 unique queries
        for (int i = 0; i < 1000; i++) {
            buffer.add("query-" + (i % 50));
        }

        assertEquals(1000, buffer.getSize(), "Buffer should have 1000 items");

        // Drain and check: 1000 searches → 50 unique queries → 50 DB writes
        Map<String, Long> aggregated = buffer.drainAll();
        assertEquals(50, aggregated.size(), "Should aggregate to 50 unique queries");

        // Each query should have count = 20 (1000 / 50)
        for (Map.Entry<String, Long> entry : aggregated.entrySet()) {
            assertEquals(20L, entry.getValue(),
                    entry.getKey() + " should have count 20");
        }

        // Summary: 1000 searches → 50 DB writes (95% reduction!)
        System.out.println("Write reduction: 1000 searches → " + aggregated.size()
                + " DB writes (" + (100 - aggregated.size() * 100 / 1000) + "% reduction)");
    }

    @Test
    @DisplayName("SearchBuffer should be thread-safe under concurrent access")
    void testBufferThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int queriesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < queriesPerThread; j++) {
                    buffer.add("thread-" + threadId + "-query-" + j);
                }
            });
        }

        // Start all threads simultaneously
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // All queries should be in the buffer
        assertEquals(threadCount * queriesPerThread, buffer.getSize(),
                "All queries should be buffered");

        // Drain should get everything
        Map<String, Long> aggregated = buffer.drainAll();
        assertEquals(threadCount * queriesPerThread, aggregated.size(),
                "All queries should be unique (thread-query combination)");
    }
}
