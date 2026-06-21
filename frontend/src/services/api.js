const BASE_URL = 'http://localhost:8080/api';

/**
 * API Service — Centralized HTTP client for the Search Typeahead backend.
 *
 * All functions return parsed JSON responses.
 * Uses the Fetch API (no external dependencies).
 * The proxy in package.json routes /api/* to http://localhost:8080.
 */

/**
 * Get autocomplete suggestions for a prefix.
 * @param {string} prefix - The search prefix
 * @returns {Promise<Array<{query: string, count: number}>>}
 */
export const getSuggestions = async (prefix) => {
  const response = await fetch(
    `${BASE_URL}/suggest?q=${encodeURIComponent(prefix)}`
  );
  if (!response.ok) throw new Error('Failed to fetch suggestions');
  return response.json();
};

/**
 * Submit a search query.
 * @param {string} query - The search query
 * @returns {Promise<{message: string}>}
 */
export const submitSearch = async (query) => {
  const response = await fetch(`${BASE_URL}/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query }),
  });
  if (!response.ok) throw new Error('Failed to submit search');
  return response.json();
};

/**
 * Get trending searches.
 * @returns {Promise<Array<{query: string, score: number}>>}
 */
export const getTrending = async () => {
  const response = await fetch(`${BASE_URL}/trending`);
  if (!response.ok) throw new Error('Failed to fetch trending');
  return response.json();
};

/**
 * Get system performance metrics.
 * @returns {Promise<{avgLatency, p95Latency, cacheHitRate, dbReads, dbWrites, batchWrites, totalSearches}>}
 */
export const getMetrics = async () => {
  const response = await fetch(`${BASE_URL}/metrics`);
  if (!response.ok) throw new Error('Failed to fetch metrics');
  return response.json();
};

/**
 * Get cache debug info for a prefix.
 * @param {string} prefix - The prefix to inspect
 * @returns {Promise<{prefix, cacheNode, hash, hit}>}
 */
export const getCacheDebug = async (prefix) => {
  const response = await fetch(
    `${BASE_URL}/cache/debug?prefix=${encodeURIComponent(prefix)}`
  );
  if (!response.ok) throw new Error('Failed to fetch cache debug');
  return response.json();
};
