import { useState, useEffect } from 'react';

/**
 * useDebounce — Custom hook to debounce a value.
 *
 * Delays updating the output value until the input hasn't changed
 * for the specified delay period. Essential for preventing excessive
 * API calls during rapid typing.
 *
 * Without debounce: "iphone" → 6 API calls (i, ip, iph, ipho, iphon, iphone)
 * With 300ms debounce: "iphone" → 1 API call (iphone, after 300ms pause)
 *
 * @param {*} value - The value to debounce
 * @param {number} delay - Debounce delay in milliseconds (default: 300)
 * @returns {*} The debounced value
 */
export function useDebounce(value, delay = 300) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    // Set a timer to update the debounced value
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    // Clear timer if value changes before delay expires
    // (this is the debounce mechanism)
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}
