import { PAGE_SIZES } from '../utils/constants';

/**
 * Server-side pagination controls.
 *
 * The API returns `totalElements` / `totalPages`, so the component never needs the full data set.
 * A window of page buttons keeps the bar usable when there are hundreds of pages.
 */
export default function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onChange,
  onSizeChange,
  itemName = 'items',
}) {
  if (!totalElements) return null;

  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  const pages = pageWindow(page, totalPages);

  return (
    <nav className="pagination" aria-label="Pagination">
      <p className="pagination__summary" aria-live="polite">
        Showing <strong>{from}</strong>–<strong>{to}</strong> of <strong>{totalElements}</strong>{' '}
        {itemName}
      </p>

      <div className="pagination__controls">
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => onChange(page - 1)}
          disabled={page === 0}
        >
          ← Prev
        </button>

        <div className="pagination__pages">
          {pages.map((entry, index) =>
            entry === '…' ? (
              <span key={`gap-${index}`} className="pagination__gap">
                …
              </span>
            ) : (
              <button
                key={entry}
                type="button"
                className={`pagination__page${entry === page ? ' is-active' : ''}`}
                onClick={() => onChange(entry)}
                aria-current={entry === page ? 'page' : undefined}
                aria-label={`Page ${entry + 1}`}
              >
                {entry + 1}
              </button>
            ),
          )}
        </div>

        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
        >
          Next →
        </button>
      </div>

      {onSizeChange ? (
        <label className="pagination__size">
          <span>Rows</span>
          <select value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
            {PAGE_SIZES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </nav>
  );
}

function pageWindow(current, total, radius = 1) {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index);

  const pages = new Set([0, total - 1, current]);
  for (let offset = 1; offset <= radius; offset += 1) {
    if (current - offset > 0) pages.add(current - offset);
    if (current + offset < total - 1) pages.add(current + offset);
  }

  const sorted = [...pages].sort((a, b) => a - b);
  const withGaps = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) withGaps.push('…');
    withGaps.push(value);
  });
  return withGaps;
}
