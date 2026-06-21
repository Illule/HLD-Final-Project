import React, { useState, useEffect } from 'react';
import { getTrending } from '../services/api';
import './TrendingSearches.css';

/**
 * TrendingSearches — Displays trending search queries with scores.
 *
 * Features:
 * - Auto-refreshes every 30 seconds
 * - Score bar visualization
 * - Gradient cards with hover effects
 * - Fire emoji for top trends
 * - Shimmer loading skeleton
 */
function TrendingSearches() {
  const [trending, setTrending] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchTrending = async () => {
    try {
      const data = await getTrending();
      setTrending(data || []);
      setError(null);
    } catch (err) {
      setError('Unable to load trending searches');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchTrending();

    // Auto-refresh every 30 seconds
    const interval = setInterval(fetchTrending, 30000);
    return () => clearInterval(interval);
  }, []);

  if (isLoading) {
    return (
      <section className="trending-section" aria-label="Trending searches">
        <h2 className="trending-title">
          <span className="trending-icon">🔥</span>
          Trending Searches
        </h2>
        <div className="trending-grid">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="trending-card skeleton">
              <div className="skeleton-text" />
              <div className="skeleton-bar" />
            </div>
          ))}
        </div>
      </section>
    );
  }

  if (error || trending.length === 0) {
    return null; // Silently hide if no trending data
  }

  // Get the max score for bar width calculation
  const maxScore = Math.max(...trending.map(t => t.score), 0.01);

  return (
    <section className="trending-section" aria-label="Trending searches">
      <h2 className="trending-title">
        <span className="trending-icon">🔥</span>
        Trending Searches
        <span className="trending-badge">Live</span>
      </h2>

      <div className="trending-grid">
        {trending.slice(0, 12).map((item, index) => (
          <div
            key={item.query}
            className={`trending-card ${index < 3 ? 'top-trend' : ''}`}
            style={{ animationDelay: `${index * 0.05}s` }}
          >
            <div className="trending-card-header">
              <span className="trending-rank">#{index + 1}</span>
              <span className="trending-score">{(item.score * 100).toFixed(0)}%</span>
            </div>

            <p className="trending-query">{item.query}</p>

            {/* Score bar visualization */}
            <div className="trending-bar-bg">
              <div
                className="trending-bar-fill"
                style={{ width: `${(item.score / maxScore) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export default TrendingSearches;
