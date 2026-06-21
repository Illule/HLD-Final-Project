package com.typeahead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SearchRequestDTO — Request payload for POST /api/search.
 *
 * Contains the search query submitted by the user.
 * Validated to ensure non-empty input with reasonable length bounds.
 *
 * Example JSON:
 * {
 *   "query": "iphone charger"
 * }
 */
public class SearchRequestDTO {

    @NotBlank(message = "Search query cannot be empty")
    @Size(min = 1, max = 500, message = "Query must be between 1 and 500 characters")
    private String query;

    public SearchRequestDTO() {}

    public SearchRequestDTO(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
