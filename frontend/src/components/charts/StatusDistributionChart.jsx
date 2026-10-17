import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import ChartFrame from './ChartFrame';
import { STATUS_META } from '../../utils/constants';
import { formatPercent } from '../../utils/format';
import { tooltipStyle } from './MonthlyTrendChart';

/**
 * Status distribution donut.
 *
 * The legend is rendered as HTML (not inside the SVG) so each slice has a readable label, a count and
 * a percentage - colour alone is never the only way to identify a status.
 */
export default function StatusDistributionChart({ slices = [], total = 0, loading = false }) {
  const data = slices
    .filter((slice) => slice.count > 0)
    .map((slice) => ({
      name: STATUS_META[slice.status]?.label || slice.status,
      status: slice.status,
      value: slice.count,
      share: slice.share,
      color: STATUS_META[slice.status]?.color || '#94a3b8',
    }));

  const table = {
    columns: ['Status', 'Applications', 'Share'],
    rows: slices.map((slice) => [
      STATUS_META[slice.status]?.label || slice.status,
      slice.count,
      formatPercent(slice.share),
    ]),
  };

  return (
    <ChartFrame
      title="Status breakdown"
      subtitle="Where every tracked application currently sits"
      table={table}
      height={300}
    >
      {loading ? (
        <div className="chart-skeleton" aria-hidden="true" />
      ) : data.length === 0 ? (
        <div className="chart-empty">
          <p>No applications yet</p>
          <small>Track your first application to see the breakdown.</small>
        </div>
      ) : (
        <div className="donut-layout">
          <div className="donut-chart">
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={data}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={58}
                  outerRadius={88}
                  paddingAngle={2}
                  stroke="#fff"
                  strokeWidth={2}
                >
                  {data.map((entry) => (
                    <Cell key={entry.status} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value, name) => [`${value} (${formatPercent(value / (total || 1))})`, name]}
                />
              </PieChart>
            </ResponsiveContainer>
            <div className="donut-center" aria-hidden="true">
              <strong>{total}</strong>
              <span>total</span>
            </div>
          </div>

          <ul className="legend">
            {slices.map((slice) => (
              <li key={slice.status} className="legend__item">
                <span
                  className="legend__swatch"
                  style={{ background: STATUS_META[slice.status]?.color || '#94a3b8' }}
                  aria-hidden="true"
                />
                <span className="legend__label">
                  {STATUS_META[slice.status]?.label || slice.status}
                </span>
                <span className="legend__value">{slice.count}</span>
                <span className="legend__share">{formatPercent(slice.share, 0)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </ChartFrame>
  );
}
