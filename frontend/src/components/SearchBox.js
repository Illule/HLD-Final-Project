import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useDebounce } from '../hooks/useDebounce';
import { getSuggestions, submitSearch } from '../services/api';
import Suggestions from './Suggestions';
import './SearchBox.css';

/**
 * SearchBox — Main search input with typeahead suggestions.
 *
 * Features:
 * - 300ms debounced API calls
 * - Keyboard navigation (↑↓ arrows, Enter, Escape)
 * - Click-outside to close suggestions
 * - Loading spinner
 * - Error state
 * - Glassmorphism design
 */
function SearchBox({ onSearchComplete }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);

  const inputRef = useRef(null);
  const containerRef = useRef(null);

  // Debounce the query to avoid excessive API calls
  const debouncedQuery = useDebounce(query, 300);

  // Fetch suggestions when debounced query changes
  useEffect(() => {
    if (!debouncedQuery.trim()) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    let cancelled = false;

    const fetchSuggestions = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const data = await getSuggestions(debouncedQuery);
        if (!cancelled) {
          setSuggestions(data || []);
          setShowSuggestions(data && data.length > 0);
          setSelectedIndex(-1);
        }
      } catch (err) {
        if (!cancelled) {
          setError('Failed to fetch suggestions');
          setSuggestions([]);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    fetchSuggestions();

    return () => { cancelled = true; };
  }, [debouncedQuery]);

  // Handle search submission
  const handleSearch = useCallback(async (searchQuery) => {
    const q = (searchQuery || query).trim();
    if (!q) return;

    try {
      await submitSearch(q);
      setShowSuggestions(false);
      onSearchComplete?.(q);
    } catch (err) {
      console.error('Search submission failed:', err);
    }
  }, [query, onSearchComplete]);

  // Handle keyboard navigation
  const handleKeyDown = useCallback((e) => {
    if (!showSuggestions || suggestions.length === 0) {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleSearch();
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex(prev =>
          prev < suggestions.length - 1 ? prev + 1 : 0
        );
        break;

      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex(prev =>
          prev > 0 ? prev - 1 : suggestions.length - 1
        );
        break;

      case 'Enter':
        e.preventDefault();
        if (selectedIndex >= 0 && selectedIndex < suggestions.length) {
          const selected = suggestions[selectedIndex].query;
          setQuery(selected);
          handleSearch(selected);
        } else {
          handleSearch();
        }
        break;

      case 'Escape':
        setShowSuggestions(false);
        setSelectedIndex(-1);
        inputRef.current?.blur();
        break;

      default:
        break;
    }
  }, [showSuggestions, suggestions, selectedIndex, handleSearch]);

  // Click outside to close suggestions
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Handle suggestion selection
  const handleSuggestionSelect = useCallback((suggestion) => {
    setQuery(suggestion.query);
    setShowSuggestions(false);
    handleSearch(suggestion.query);
  }, [handleSearch]);

  const hasSuggestions = showSuggestions && suggestions.length > 0;

  return (
    <div className="search-box-container" ref={containerRef}>
      <div className={`search-input-wrapper ${hasSuggestions ? 'has-suggestions' : ''} ${isLoading ? 'is-loading' : ''}`}>
        {/* Search Icon */}
        <svg className="search-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
          <circle cx="8.5" cy="8.5" r="6" stroke="currentColor" strokeWidth="2"/>
          <line x1="13" y1="13" x2="18" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </svg>

        {/* Input Field */}
        <input
          ref={inputRef}
          id="search-input"
          type="text"
          className="search-input"
          placeholder="Search anything..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            if (suggestions.length > 0) setShowSuggestions(true);
          }}
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          spellCheck="false"
          role="combobox"
          aria-expanded={hasSuggestions}
          aria-haspopup="listbox"
          aria-autocomplete="list"
        />

        {/* Loading Spinner */}
        {isLoading && (
          <div className="search-spinner" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
        )}

        {/* Clear Button */}
        {query && !isLoading && (
          <button
            className="search-clear"
            onClick={() => {
              setQuery('');
              setSuggestions([]);
              setShowSuggestions(false);
              inputRef.current?.focus();
            }}
            aria-label="Clear search"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <line x1="4" y1="4" x2="12" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              <line x1="12" y1="4" x2="4" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </button>
        )}

        {/* Search Button */}
        <button
          id="search-button"
          className="search-button"
          onClick={() => handleSearch()}
          aria-label="Search"
        >
          <span className="search-button-text">Search</span>
          <svg className="search-button-icon" width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M3 9h12M11 5l4 4-4 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
      </div>

      {/* Error State */}
      {error && (
        <div className="search-error" role="alert">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="2"/>
            <line x1="8" y1="5" x2="8" y2="9" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            <circle cx="8" cy="12" r="1" fill="currentColor"/>
          </svg>
          {error}
        </div>
      )}

      {/* Suggestions Dropdown */}
      {hasSuggestions && (
        <Suggestions
          suggestions={suggestions}
          selectedIndex={selectedIndex}
          onSelect={handleSuggestionSelect}
          query={query}
        />
      )}
    </div>
  );
}

export default SearchBox;
