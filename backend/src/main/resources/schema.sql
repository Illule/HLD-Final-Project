-- ============================================================
-- Search Typeahead System — Database Schema Reference
-- ============================================================
-- This file is NOT auto-executed by Spring Boot (sql.init.mode=never).
-- Hibernate creates the schema from JPA entity annotations.
-- This file serves as documentation and for manual PostgreSQL setup.
-- ============================================================

-- Main table: stores all searchable queries with frequency counts
CREATE TABLE IF NOT EXISTS search_query (
    id          BIGSERIAL PRIMARY KEY,                          -- Auto-incrementing primary key
    query       VARCHAR(500) UNIQUE NOT NULL,                   -- The search query text (lowercase, unique)
    total_count BIGINT DEFAULT 0 NOT NULL,                      -- All-time search frequency
    recent_count BIGINT DEFAULT 0 NOT NULL,                     -- Recent search frequency (decays hourly)
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL    -- Last update timestamp
);

-- Index on query: O(log n) lookups for exact match and prefix search
-- Used by: SuggestionService (LIKE 'prefix%'), BatchWriter (exact match)
CREATE INDEX IF NOT EXISTS idx_search_query_query ON search_query(query);

-- Index on total_count (descending): O(log n) for top-K queries
-- Used by: TrendingService (ORDER BY total_count DESC LIMIT K)
CREATE INDEX IF NOT EXISTS idx_search_query_total_count ON search_query(total_count DESC);

-- ============================================================
-- Design Notes:
--
-- 1. query is UNIQUE: prevents duplicate entries; upsert logic in BatchWriter
-- 2. total_count: monotonically increasing; reflects all-time popularity
-- 3. recent_count: decays via exponential halving every hour
--    - Enables recency-aware trending: score = 0.7*norm(total) + 0.3*norm(recent)
-- 4. updated_at: tracks freshness; useful for cache invalidation decisions
--
-- Space Complexity: ~100 bytes per row
--   100K queries ≈ 10MB (trivial for any database)
--   1M queries ≈ 100MB (still manageable)
--
-- PostgreSQL Setup:
--   CREATE DATABASE typeahead;
--   \c typeahead
--   \i schema.sql
-- ============================================================
