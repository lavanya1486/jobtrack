/** Formatting helpers. Everything date-related goes through here so the UI is consistent. */

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDate(value) {
  if (!value) return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return DATE_FORMAT.format(date);
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return TIME_FORMAT.format(date);
}

/** "in 3 days", "2 days ago", "today". */
export function formatRelative(value) {
  if (!value) return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';

  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const target = new Date(date);
  const diffDays = Math.round((target - startOfToday) / 86400000);

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Tomorrow';
  if (diffDays === -1) return 'Yesterday';
  if (diffDays > 1) return `In ${diffDays} days`;
  return `${Math.abs(diffDays)} days ago`;
}

export function formatPercent(rate, digits = 1) {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return '—';
  return `${(rate * 100).toFixed(digits)}%`;
}

export function formatNumber(value) {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat('en-IN').format(value);
}

/** 1200000 -> "12L" for INR, "1.2M" otherwise; keeps table cells narrow. */
export function formatCompactMoney(value, currency = 'INR') {
  if (value === null || value === undefined) return null;
  const amount = Number(value);
  if (Number.isNaN(amount)) return null;

  if (currency === 'INR') {
    if (Math.abs(amount) >= 1e7) return `₹${(amount / 1e7).toFixed(1)}Cr`;
    if (Math.abs(amount) >= 1e5) return `₹${(amount / 1e5).toFixed(1)}L`;
    if (Math.abs(amount) >= 1e3) return `₹${(amount / 1e3).toFixed(0)}K`;
    return `₹${amount.toFixed(0)}`;
  }
  const symbol = currencySymbol(currency);
  if (Math.abs(amount) >= 1e6) return `${symbol}${(amount / 1e6).toFixed(1)}M`;
  if (Math.abs(amount) >= 1e3) return `${symbol}${(amount / 1e3).toFixed(0)}K`;
  return `${symbol}${amount.toFixed(0)}`;
}

export function currencySymbol(currency = 'INR') {
  switch (currency) {
    case 'INR':
      return '₹';
    case 'USD':
      return '$';
    case 'EUR':
      return '€';
    case 'GBP':
      return '£';
    case 'AED':
      return 'AED ';
    default:
      return `${currency} `;
  }
}

export function formatSalaryRange(min, max, currency = 'INR') {
  const low = formatCompactMoney(min, currency);
  const high = formatCompactMoney(max, currency);
  if (low && high) return `${low} – ${high}`;
  return low || high || '—';
}

/** SNAKE_CASE -> "Snake case" for enum labels. */
export function titleCase(value) {
  if (!value) return '';
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function todayInputValue() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

/** ISO instant -> value for <input type="datetime-local"> in the browser's zone. */
export function toDateTimeInputValue(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

/** <input type="datetime-local"> value -> ISO instant (UTC). */
export function fromDateTimeInputValue(value) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

/** Default interview slot: tomorrow at 10:30 local, as an ISO instant. */
export function defaultInterviewSlot() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(10, 30, 0, 0);
  return date.toISOString();
}

export function pluralise(count, singular, plural) {
  return count === 1 ? singular : plural || `${singular}s`;
}

export function initials(name = '') {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

/** Turns the API's fieldErrors array into a { field: message } map for forms. */
export function fieldErrorMap(fieldErrors = []) {
  return fieldErrors.reduce((accumulator, error) => {
    if (error?.field) accumulator[error.field] = error.message;
    return accumulator;
  }, {});
}
