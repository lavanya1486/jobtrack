import ChartFrame from './ChartFrame';
import { formatPercent } from '../../utils/format';

/**
 * Interview conversion funnel.
 *
 * Rendered as proportional HTML bars rather than a chart library funnel: the numbers stay in the DOM
 * (readable by screen readers and searchable), and the width is derived from the API's own counts, so
 * nothing is hardcoded.
 */
export default function FunnelChart({ stages = [], loading = false }) {
  const max = Math.max(1, ...stages.map((stage) => stage.count));
  const table = {
    columns: ['Stage', 'Applications', 'From previous stage', 'From start'],
    rows: stages.map((stage) => [
      stage.label,
      stage.count,
      formatPercent(stage.conversionFromPrevious),
      formatPercent(stage.conversionFromStart),
    ]),
  };

  return (
    <ChartFrame
      title="Conversion funnel"
      subtitle="Applications that reached each stage (from the status audit trail)"
      table={table}
      height={260}
    >
      {loading ? (
        <div className="chart-skeleton" aria-hidden="true" />
      ) : (
        <ol className="funnel">
          {stages.map((stage, index) => (
            <li key={stage.stage} className="funnel__row">
              <div className="funnel__label">
                <span>{stage.label}</span>
                <strong>{stage.count}</strong>
              </div>
              <div
                className="funnel__track"
                role="img"
                aria-label={`${stage.label}: ${stage.count} applications, ${formatPercent(
                  stage.conversionFromStart,
                )} of submitted`}
              >
                <span
                  className={`funnel__bar funnel__bar--${index}`}
                  style={{ width: `${Math.max(2, (stage.count / max) * 100)}%` }}
                />
              </div>
              <div className="funnel__meta">
                <span>{formatPercent(stage.conversionFromPrevious, 0)} of previous</span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </ChartFrame>
  );
}
