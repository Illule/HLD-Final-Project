import React, { useState, useEffect } from 'react';
import { getMetrics } from '../services/api';
import './MetricsDashboard.css';

/**
 * MetricsDashboard — System performance metrics visualization.
 *
 * Displays key metrics in a clean card grid:
 * - Average latency
 * - P95 latency
 * - Cache hit rate
 * - DB reads/writes
 * - Batch writes
 * - Total searches
 *
 * Auto-refreshes every 5 seconds.
 */
function MetricsDashboard() {
  const [metrics, setMetrics] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExpanded, setIsExpanded] = useState(false);

  const fetchMetrics = async () => {
    try {
      const data = await getMetrics();
      setMetrics(data);
    } catch (err) {
      // Silently ignore — metrics are non-critical
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, []);

  if (isLoading || !metrics) return null;

  const metricCards = [
    {
      label: 'Avg Latency',
      value: `${metrics.avgLatency}ms`,
      icon: '⚡',
      color: 'blue',
      detail: 'Mean response time'
    },
    {
      label: 'P95 Latency',
      value: `${metrics.p95Latency}ms`,
      icon: '📊',
      color: 'purple',
      detail: '95th percentile'
    },
    {
      label: 'Cache Hit Rate',
      value: `${metrics.cacheHitRate}%`,
      icon: '🎯',
      color: metrics.cacheHitRate >= 80 ? 'green' : metrics.cacheHitRate >= 50 ? 'orange' : 'red',
      detail: 'Requests served from cache'
    },
    {
      label: 'DB Reads',
      value: formatNumber(metrics.dbReads),
      icon: '📖',
      color: 'blue',
      detail: 'Database read operations'
    },
    {
      label: 'DB Writes',
      value: formatNumber(metrics.dbWrites),
      icon: '✍️',
      color: 'orange',
      detail: 'Database write operations'
    },
    {
      label: 'Batch Writes',
      value: formatNumber(metrics.batchWrites),
      icon: '📦',
      color: 'purple',
      detail: 'Batch flush operations'
    },
    {
      label: 'Total Searches',
      value: formatNumber(metrics.totalSearches),
      icon: '🔍',
      color: 'green',
      detail: 'Search submissions received'
    }
  ];

  return (
    <section className="metrics-section" aria-label="System metrics">
      <button
        className="metrics-toggle"
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
      >
        <h2 className="metrics-title">
          <span className="metrics-icon">📊</span>
          System Metrics
        </h2>
        <svg
          className={`metrics-chevron ${isExpanded ? 'expanded' : ''}`}
          width="20" height="20" viewBox="0 0 20 20" fill="none"
        >
          <path d="M5 8l5 5 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
      </button>

      {isExpanded && (
        <div className="metrics-grid">
          {metricCards.map((card) => (
            <div key={card.label} className={`metric-card metric-${card.color}`}>
              <div className="metric-header">
                <span className="metric-emoji">{card.icon}</span>
                <span className="metric-label">{card.label}</span>
              </div>
              <div className="metric-value">{card.value}</div>
              <div className="metric-detail">{card.detail}</div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * Format large numbers for display.
 */
function formatNumber(num) {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
}

export default MetricsDashboard;
