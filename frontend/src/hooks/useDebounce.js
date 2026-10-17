import { useEffect, useState } from 'react';

/**
 * Delays a fast-changing value (search box) so a keystroke does not fire a request.
 * The list endpoint is server-side paginated, so debouncing keeps both the DB and the UI calm.
 */
export function useDebounce(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}

export default useDebounce;
