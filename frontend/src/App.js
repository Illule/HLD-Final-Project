import React, { useState, useCallback } from 'react';
import SearchBox from './components/SearchBox';
import MetricsDashboard from './components/MetricsDashboard';
import './App.css';

/**
 * App — Root component for the Search Typeahead UI.
 *
 * Layout:
 *   Hero title → Search box → Trending searches → Metrics dashboard
 *
 * Design inspired by Google Search with a premium dark theme,
 * glassmorphism effects, and smooth animations.
 */
function App() {
  const [searchPerformed, setSearchPerformed] = useState(false);
  const [searchResult, setSearchResult] = useState('');

  const handleSearchComplete = useCallback((query) => {
    setSearchPerformed(true);
    setSearchResult(query || '');

    // Clear the result message after 3 seconds
    setTimeout(() => {
      setSearchPerformed(false);
      setSearchResult('');
    }, 3000);
  }, []);

  return (
    <div className="app">
      {/* ---- Hero Section ---- */}
      <header className="app-header">
        <div className="logo-container">
          <div className="logo-icon">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
              <circle cx="18" cy="18" r="12" stroke="url(#grad)" strokeWidth="3" fill="none"/>
              <line x1="27" y1="27" x2="36" y2="36" stroke="url(#grad)" strokeWidth="3" strokeLinecap="round"/>
              <defs>
                <linearGradient id="grad" x1="0" y1="0" x2="40" y2="40">
                  <stop offset="0%" stopColor="#4285f4"/>
                  <stop offset="100%" stopColor="#8b5cf6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h1 className="logo-text">
            <span className="logo-type">Type</span>
            <span className="logo-ahead">Ahead</span>
          </h1>
        </div>
        <p className="tagline">Lightning-fast search with distributed caching</p>
      </header>

      {/* ---- Search Section ---- */}
      <main className="app-main">
        <SearchBox onSearchComplete={handleSearchComplete} />

        {/* Search result notification */}
        {searchPerformed && (
          <div className="search-notification" role="alert">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="7" stroke="#34d399" strokeWidth="2"/>
              <path d="M5 8l2 2 4-4" stroke="#34d399" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <span>Searched for "<strong>{searchResult}</strong>"</span>
          </div>
        )}

        {/* ---- Trending Searches ---- */}

        {/* ---- System Metrics Dashboard ---- */}
        <MetricsDashboard />
      </main>

      {/* ---- Footer ---- */}
      <footer className="app-footer">
        <p>
          Search Typeahead System — Built with Spring Boot + React
          <span className="footer-dot">•</span>
          Distributed Cache with Consistent Hashing
          <span className="footer-dot">•</span>
          Batch Writes
        </p>
      </footer>
    </div>
  );
}

export default App;
