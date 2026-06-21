package com.typeahead.controller;

import com.typeahead.dto.TrendingDTO;
import com.typeahead.service.TrendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TrendingController — REST endpoint for trending searches.
 *
 * GET /api/trending
 *
 * Returns the top trending searches with recency-aware scores.
 * Results are cached for 30 seconds and invalidated after batch writes.
 */
@RestController
@RequestMapping("/api")
public class TrendingController {

    private static final Logger log = LoggerFactory.getLogger(TrendingController.class);

    private final TrendingService trendingService;

    public TrendingController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    /**
     * Get trending searches.
     *
     * @return JSON array of trending queries: [{"query":"chatgpt","score":0.95}, ...]
     */
    @GetMapping("/trending")
    public ResponseEntity<List<TrendingDTO>> trending() {
        log.debug("[API] GET /trending");
        List<TrendingDTO> trending = trendingService.getTrending();
        return ResponseEntity.ok(trending);
    }
}
