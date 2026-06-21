import React from 'react';
import './Suggestions.css';

/**
 * Suggestions — Dropdown list of autocomplete suggestions.
 *
 * Features:
 * - Highlights the matching prefix in each suggestion
 * - Keyboard-navigable (selected index from parent)
 * - Click to select
 * - Smooth slide-down animation
 * - Formatted count display
 */
function Suggestions({ suggestions, selectedIndex, onSelect, query }) {
  /**
   * Highlight the matching prefix portion of a suggestion.
   * The prefix is rendered in normal weight; the rest is bold.
   */
  const highlightMatch = (text, prefix) => {
    const normalizedPrefix = (prefix || '').toLowerCase();
    const normalizedText = text.toLowerCase();

    if (normalizedText.startsWith(normalizedPrefix)) {
      const matchEnd = normalizedPrefix.length;
      return (
        <>
          <span className="suggestion-match">{text.slice(0, matchEnd)}</span>
          <span className="suggestion-rest">{text.slice(matchEnd)}</span>
        </>
      );
    }

    return <span>{text}</span>;
  };

  /**
   * Format large numbers with K/M suffixes for readability.
   * 85000 → "85K", 1500000 → "1.5M"
   */
  const formatCount = (count) => {
    if (count >= 1000000) {
      return (count / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
    }
    if (count >= 1000) {
      return (count / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
    }
    return count.toString();
  };

  return (
    <ul className="suggestions-list" role="listbox" id="suggestions-listbox">
      {suggestions.map((suggestion, index) => (
        <li
          key={suggestion.query}
          className={`suggestion-item ${index === selectedIndex ? 'selected' : ''}`}
          role="option"
          aria-selected={index === selectedIndex}
          onClick={() => onSelect(suggestion)}
          onMouseEnter={(e) => e.currentTarget.classList.add('hovered')}
          onMouseLeave={(e) => e.currentTarget.classList.remove('hovered')}
        >
          {/* Search icon for each suggestion */}
          <svg className="suggestion-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
            <circle cx="7" cy="7" r="5" stroke="currentColor" strokeWidth="1.5"/>
            <line x1="10.5" y1="10.5" x2="14" y2="14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
          </svg>

          {/* Suggestion text with highlighted prefix */}
          <span className="suggestion-text">
            {highlightMatch(suggestion.query, query)}
          </span>

          {/* Search count badge */}
          <span className="suggestion-count">
            {formatCount(suggestion.count)}
          </span>
        </li>
      ))}
    </ul>
  );
}

export default Suggestions;
