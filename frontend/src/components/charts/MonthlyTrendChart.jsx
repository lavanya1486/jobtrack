import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import ChartFrame, { ChartEmpty } from './ChartFrame';
import { CHART_COLORS } from '../../utils/constants';

/**
 * Monthly application trend: bars for applications submitted, lines for interviews and offers.
 * Zero-filled months come from the API, so the axis is continuous.
 */
export default function MonthlyTrendChart({ data = [], loading = false }) {
  const hasData = data.some((point) => point.applications > 0 || point.interviews > 0);
  const table = {
    columns: ['Month', 'Applications', 'Interviews', 'Responses', 'Offers', 'Rejections'],
    rows: data.map((point) => [
      point.label,
      point.applications,
      point.interviews,
      point.responses,
      point.offers,
      point.rejections,
    ]),
  };

  return (
    <ChartFrame
      title="Applications per month"
      subtitle="Submitted applications, interview rounds and offers by month"
      table={table}
      loading={loading}
      height={300}
      footer={
        data.length ? (
          <p className="muted-text">
            Window: {data[0]?.label} – {data[data.length - 1]?.label}
          </p>
        ) : null
      }
    >
      {loading ? (
        <div className="chart-skeleton" aria-hidden="true" />
      ) : !hasData ? (
        <ChartEmpty message="No applications in this window yet" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -18 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
            <XAxis
              dataKey="month"
              tick={{ fontSize: 12, fill: CHART_COLORS.axis }}
              tickLine={false}
              axisLine={{ stroke: CHART_COLORS.grid }}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fontSize: 12, fill: CHART_COLORS.axis }}
              tickLine={false}
              axisLine={false}
              width={44}
            />
            <Tooltip
              cursor={{ fill: 'rgba(79, 70, 229, 0.06)' }}
              contentStyle={tooltipStyle}
              formatter={(value, name) => [value, labelFor(name)]}
            />
            <Legend formatter={(value) => labelFor(value)} iconType="circle" />
            <Bar
              dataKey="applications"
              name="applications"
              fill={CHART_COLORS.applications}
              radius={[6, 6, 0, 0]}
              maxBarSize={38}
            />
            <Line
              type="monotone"
              dataKey="interviews"
              name="interviews"
              stroke={CHART_COLORS.interviews}
              strokeWidth={2.5}
              dot={{ r: 3 }}
            />
            <Line
              type="monotone"
              dataKey="responses"
              name="responses"
              stroke={CHART_COLORS.responses}
              strokeWidth={2}
              strokeDasharray="4 3"
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="offers"
              name="offers"
              stroke={CHART_COLORS.offers}
              strokeWidth={2.5}
              dot={{ r: 3 }}
            />
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  );
}

export const tooltipStyle = {
  borderRadius: 10,
  border: '1px solid #e2e8f0',
  boxShadow: '0 10px 30px rgba(15, 23, 42, 0.12)',
  fontSize: 12,
};

function labelFor(key) {
  switch (key) {
    case 'applications':
      return 'Applications';
    case 'interviews':
      return 'Interviews';
    case 'responses':
      return 'Responses';
    case 'offers':
      return 'Offers';
    case 'rejections':
      return 'Rejections';
    default:
      return key;
  }
}
