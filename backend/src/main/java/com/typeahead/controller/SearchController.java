package com.typeahead.controller;

import com.typeahead.dto.SearchRequestDTO;
import com.typeahead.dto.SearchResponseDTO;
import com.typeahead.service.SearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SearchController — REST endpoint for search submissions.
 *
 * POST /api/search
 *
 * Accepts a search query and buffers it for batch processing.
 * Returns immediate acknowledgment — the DB write happens asynchronously.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Submit a search query.
     *
     * @param request JSON body with "query" field
     * @return {"message": "Searched"}
     *
     * The query is validated (@NotBlank, @Size) then buffered
     * for eventual batch processing by the BatchWriter.
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponseDTO> search(
            @Valid @RequestBody SearchRequestDTO request) {

        log.debug("[API] POST /search query=\"{}\"", request.getQuery());

        SearchResponseDTO response = searchService.submitSearch(request.getQuery());
        return ResponseEntity.ok(response);
    }
}
