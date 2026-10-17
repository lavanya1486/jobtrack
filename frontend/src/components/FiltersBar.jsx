import { useMemo } from 'react';
import { EMPLOYMENT_TYPES, SORT_OPTIONS, STATUSES, STATUS_META, WORK_MODES } from '../utils/constants';
import { titleCase } from '../utils/format';

/**
 * Search / filter / sort bar for the applications table.
 *
 * Every control is a controlled input that reports a patch upwards; the page owns the state and
 * re-queries the server. Filters are combined server-side, so this component never filters rows in
 * the browser.
 */
export default function FiltersBar({ filters, onChange, options, sort, onSortChange, onClear, size, onSizeChange }) {
  const activeCount = useMemo(() => countActiveFilters(filters, sort), [filters, sort]);

  function toggleStatus(status) {
    const current = filters.status || [];
    const next = current.includes(status)
      ? current.filter((entry) => entry !== status)
      : [...current, status];
    onChange({ status: next, page: 0 });
  }

  function patch(field, value) {
    onChange({ [field]: value, page: 0 });
  }

  return (
    <section className="card filters" aria-label="Filter applications">
      <div className="filters__row filters__row--primary">
        <div className="filters__search">
          <label className="visually-hidden" htmlFor="filter-search">
            Search by company, job title or location
          </label>
          <span className="filters__search-icon" aria-hidden="true">
            ⌕
          </span>
          <input
            id="filter-search"
            className="input input--search"
            type="search"
            placeholder="Search company, role or location…"
            value={filters.search || ''}
            onChange={(event) => patch('search', event.target.value)}
          />
        </div>

        <div className="filters__sort">
          <label className="visually-hidden" htmlFor="filter-sort">
            Sort applications
          </label>
          <select
            id="filter-sort"
            className="input"
            value={sort}
            onChange={(event) => onSortChange(event.target.value)}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="filters__statuses" role="group" aria-label="Filter by status">
        {STATUSES.map((status) => {
          const active = (filters.status || []).includes(status);
          return (
            <button
              key={status}
              type="button"
              className={`chip${active ? ' is-active' : ''}`}
              aria-pressed={active}
              onClick={() => toggleStatus(status)}
              style={active ? { borderColor: STATUS_META[status].color, color: STATUS_META[status].color } : undefined}
            >
              {STATUS_META[status].label}
            </button>
          );
        })}
      </div>

      <div className="filters__row filters__row--secondary">
        <SelectFilter
          id="employmentType"
          label="Employment"
          value={filters.employmentType?.[0] || ''}
          onChange={(value) => patch('employmentType', value ? [value] : [])}
          options={EMPLOYMENT_TYPES.map((type) => ({ value: type, label: titleCase(type) }))}
        />
        <SelectFilter
          id="workMode"
          label="Work mode"
          value={filters.workMode?.[0] || ''}
          onChange={(value) => patch('workMode', value ? [value] : [])}
          options={WORK_MODES.map((mode) => ({ value: mode, label: titleCase(mode) }))}
        />
        <SelectFilter
          id="location"
          label="Location"
          value={filters.location || ''}
          onChange={(value) => patch('location', value)}
          options={(options?.locations || []).map((location) => ({ value: location, label: location }))}
        />
        <SelectFilter
          id="tag"
          label="Tag"
          value={filters.tag || ''}
          onChange={(value) => patch('tag', value)}
          options={(options?.tags || []).map((tag) => ({ value: tag, label: `#${tag}` }))}
        />

        <div className="field field--inline">
          <label htmlFor="filter-applied-from">Applied from</label>
          <input
            id="filter-applied-from"
            type="date"
            className="input"
            value={filters.appliedFrom || ''}
            onChange={(event) => patch('appliedFrom', event.target.value)}
          />
        </div>
        <div className="field field--inline">
          <label htmlFor="filter-applied-to">to</label>
          <input
            id="filter-applied-to"
            type="date"
            className="input"
            value={filters.appliedTo || ''}
            onChange={(event) => patch('appliedTo', event.target.value)}
          />
        </div>

        <label className="switch">
          <input
            type="checkbox"
            checked={Boolean(filters.submittedOnly)}
            onChange={(event) => patch('submittedOnly', event.target.checked || null)}
          />
          <span>Submitted only</span>
        </label>

        <label className="switch">
          <input
            type="checkbox"
            checked={Boolean(filters.overdueOnly)}
            onChange={(event) => patch('overdueOnly', event.target.checked || null)}
          />
          <span>Overdue only</span>
        </label>

        {onSizeChange ? (
          <div className="field field--inline field--narrow">
            <label htmlFor="filter-size">Rows</label>
            <select
              id="filter-size"
              className="input"
              value={size}
              onChange={(event) => onSizeChange(Number(event.target.value))}
            >
              {[10, 20, 50, 100].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        <button
          type="button"
          className="btn btn--ghost btn--sm filters__clear"
          onClick={onClear}
          disabled={activeCount === 0}
        >
          Clear filters{activeCount ? ` (${activeCount})` : ''}
        </button>
      </div>
    </section>
  );
}

function SelectFilter({ id, label, value, onChange, options }) {
  return (
    <div className="field field--inline">
      <label htmlFor={`filter-${id}`}>{label}</label>
      <select
        id={`filter-${id}`}
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">All</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export function countActiveFilters(filters, sort) {
  let count = 0;
  if (filters.search) count += 1;
  if (filters.status?.length) count += 1;
  if (filters.employmentType?.length) count += 1;
  if (filters.workMode?.length) count += 1;
  if (filters.location) count += 1;
  if (filters.tag) count += 1;
  if (filters.appliedFrom) count += 1;
  if (filters.appliedTo) count += 1;
  if (filters.deadlineFrom) count += 1;
  if (filters.deadlineTo) count += 1;
  if (filters.submittedOnly) count += 1;
  if (filters.overdueOnly) count += 1;
  if (sort && sort !== 'newest') count += 1;
  return count;
}
