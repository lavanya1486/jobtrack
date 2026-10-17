/**
 * Dashboard KPI card.
 *
 * `trend` renders an accessible caption ("up 12% versus last month") rather than relying on colour
 * or an arrow glyph alone.
 */
export default function KpiCard({
  label,
  value,
  hint,
  tone = 'default',
  icon,
  loading = false,
  trend,
}) {
  return (
    <article className={`kpi kpi--${tone}`} aria-busy={loading}>
      <div className="kpi__head">
        <span className="kpi__label">{label}</span>
        {icon ? <span className="kpi__icon" aria-hidden="true">{icon}</span> : null}
      </div>
      {loading ? (
        <div className="skeleton skeleton--value" aria-hidden="true" />
      ) : (
        <p className="kpi__value">{value}</p>
      )}
      {hint ? <p className="kpi__hint">{hint}</p> : null}
      {trend ? (
        <p className={`kpi__trend kpi__trend--${trend.direction || 'flat'}`}>
          <span aria-hidden="true">
            {trend.direction === 'up' ? '▲' : trend.direction === 'down' ? '▼' : '•'}
          </span>{' '}
          {trend.label}
        </p>
      ) : null}
    </article>
  );
}
