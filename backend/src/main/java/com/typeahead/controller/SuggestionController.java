package com.typeahead.controller;

import com.typeahead.dto.SuggestionDTO;
import com.typeahead.service.SuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * SuggestionController — REST endpoint for typeahead suggestions.
 *
 * GET /api/suggest?q={prefix}
 *
 * Returns up to 10 autocomplete suggestions matching the given prefix,
 * sorted by popularity (descending total_count).
 *
 * The controller is intentionally thin — all business logic
 * (caching, DB queries, metrics) lives in SuggestionService.
 */
@RestController
@RequestMapping("/api")
public class SuggestionController {

    private static final Logger log = LoggerFactory.getLogger(SuggestionController.class);

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * Get autocomplete suggestions for a prefix.
     *
     * @param q The search prefix (e.g., "iph")
     * @return JSON array of suggestions: [{"query":"iphone 15","count":85000}, ...]
     *
     * Behavior:
     * - Empty/null prefix → returns []
     * - No matches → returns []
     * - Case-insensitive matching
     * - Max 10 results, sorted by count descending
     */
    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestionDTO>> suggest(
            @RequestParam(value = "q", required = false, defaultValue = "") String q) {

        log.debug("[API] GET /suggest?q={}", q);

        List<SuggestionDTO> suggestions = suggestionService.getSuggestions(q);
        return ResponseEntity.ok(suggestions != null ? suggestions : Collections.emptyList());
    }
}
