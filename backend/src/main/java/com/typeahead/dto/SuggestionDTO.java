package com.typeahead.dto;

/**
 * SuggestionDTO — Response payload for the /api/suggest endpoint.
 *
 * Contains the autocomplete suggestion text and its search frequency count.
 * Results are sorted by count (descending) to surface the most popular queries.
 *
 * Example JSON:
 * {
 *   "query": "iphone 15",
 *   "count": 85000
 * }
 */
public class SuggestionDTO {

    /** The suggested search query text */
    private String query;

    /** The total search frequency count (higher = more popular) */
    private long count;

    public SuggestionDTO() {}

    public SuggestionDTO(String query, long count) {
        this.query = query;
        this.count = count;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
