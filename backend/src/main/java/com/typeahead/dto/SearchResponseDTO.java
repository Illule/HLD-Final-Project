package com.typeahead.dto;

/**
 * SearchResponseDTO — Response payload for POST /api/search.
 *
 * Simple acknowledgment that the search was received and buffered.
 * The actual DB write happens asynchronously via the BatchWriter.
 *
 * Example JSON:
 * {
 *   "message": "Searched"
 * }
 */
public class SearchResponseDTO {

    private String message;

    public SearchResponseDTO() {}

    public SearchResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
