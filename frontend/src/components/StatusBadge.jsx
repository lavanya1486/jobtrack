import { STATUS_META } from '../utils/constants';

/** Status pill. The label is also the accessible text, so colour is never the only signal. */
export default function StatusBadge({ status, size = 'md' }) {
  const meta = STATUS_META[status] || { label: status || '—', tone: 'neutral' };
  return (
    <span className={`badge badge--${meta.tone} badge--${size}`}>
      {meta.label}
    </span>
  );
}

export function TagList({ tags = [] }) {
  if (!tags.length) return null;
  return (
    <ul className="tag-list" aria-label="Tags">
      {tags.map((tag) => (
        <li key={tag} className="tag">
          {tag}
        </li>
      ))}
    </ul>
  );
}
