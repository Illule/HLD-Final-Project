package com.typeahead.dto;

/**
 * TrendingDTO — Response payload for GET /api/trending.
 *
 * Contains a trending search query and its computed score.
 * Score is calculated as:
 *   score = 0.7 × normalized(totalCount) + 0.3 × normalized(recentCount)
 *
 * This formula balances all-time popularity (70%) with recent momentum (30%),
 * ensuring that newly trending topics surface while established queries
 * maintain their position.
 *
 * Example JSON:
 * {
 *   "query": "chatgpt",
 *   "score": 0.95
 * }
 */
public class TrendingDTO {

    /** The trending search query text */
    private String query;

    /** Computed trending score between 0.0 and 1.0 */
    private double score;

    public TrendingDTO() {}

    public TrendingDTO(String query, double score) {
        this.query = query;
        this.score = score;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
