/** Loading indicators: a spinner for actions and skeletons for first paint. */
export function Spinner({ label = 'Loading' }) {
  return (
    <span className="spinner" role="status" aria-live="polite">
      <span className="spinner__ring" aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </span>
  );
}

export function SkeletonRows({ rows = 5, columns = 5 }) {
  return (
    <div className="skeleton-table" aria-hidden="true">
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div className="skeleton-row" key={rowIndex}>
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <span className="skeleton skeleton--cell" key={columnIndex} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function SkeletonCards({ count = 4 }) {
  return (
    <div className="kpi-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div className="kpi" key={index}>
          <span className="skeleton skeleton--line" />
          <span className="skeleton skeleton--value" />
          <span className="skeleton skeleton--line skeleton--short" />
        </div>
      ))}
    </div>
  );
}

export default Spinner;
