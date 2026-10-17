/**
 * Accessible chart frame.
 *
 * Charts are visual-only, so every chart ships with a caption and a screen-reader data table
 * (`.visually-hidden`) carrying the same numbers. That is the difference between "a chart" and "a
 * chart a recruiter using a screen reader can read".
 */
export default function ChartFrame({ title, subtitle, children, table, footer, height = 280 }) {
  return (
    <section className="card chart-card">
      <header className="card__header">
        <div>
          <h2 className="card__title">{title}</h2>
          {subtitle ? <p className="card__subtitle">{subtitle}</p> : null}
        </div>
      </header>
      <div className="chart-card__body" style={{ height }}>
        {children}
      </div>
      {footer ? <div className="chart-card__footer">{footer}</div> : null}
      {table ? (
        <table className="visually-hidden">
          <caption>{title} data</caption>
          <thead>
            <tr>
              {table.columns.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row, index) => (
              <tr key={`${row[0]}-${index}`}>
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex}>{cell}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

export function ChartEmpty({ message = 'No data yet' }) {
  return (
    <div className="chart-empty">
      <p>{message}</p>
      <small>Add applications and the chart will fill in automatically.</small>
    </div>
  );
}
