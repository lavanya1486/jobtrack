/**
 * Shared vocabulary between the UI and the API.
 *
 * Statuses, round types and the transition matrix mirror the backend enums exactly
 * (`ApplicationStatus`, `RoundType`, `ApplicationStatusPolicy`). Keeping them here means the form can
 * disable impossible transitions before the request is sent, while the server still enforces them.
 */

export const STATUS_META = {
  SAVED: { label: 'Saved', tone: 'neutral', color: '#64748b' },
  APPLIED: { label: 'Applied', tone: 'info', color: '#2563eb' },
  ASSESSMENT: { label: 'Assessment', tone: 'warning', color: '#d97706' },
  INTERVIEW: { label: 'Interview', tone: 'accent', color: '#7c3aed' },
  OFFER: { label: 'Offer', tone: 'success', color: '#059669' },
  REJECTED: { label: 'Rejected', tone: 'danger', color: '#dc2626' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'muted', color: '#94a3b8' },
};

export const STATUSES = Object.keys(STATUS_META);

/** Mirrors `ApplicationStatusPolicy` on the server. */
export const STATUS_TRANSITIONS = {
  SAVED: ['APPLIED', 'WITHDRAWN'],
  APPLIED: ['ASSESSMENT', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'],
  ASSESSMENT: ['APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'],
  INTERVIEW: ['ASSESSMENT', 'OFFER', 'REJECTED', 'WITHDRAWN'],
  OFFER: ['REJECTED', 'WITHDRAWN'],
  REJECTED: ['APPLIED', 'ASSESSMENT', 'INTERVIEW', 'OFFER', 'WITHDRAWN'],
  WITHDRAWN: ['APPLIED', 'ASSESSMENT', 'INTERVIEW', 'OFFER', 'REJECTED'],
};

export const EMPLOYMENT_TYPES = [
  'FULL_TIME',
  'PART_TIME',
  'INTERNSHIP',
  'CONTRACT',
  'FREELANCE',
  'TEMPORARY',
];

export const WORK_MODES = ['ONSITE', 'HYBRID', 'REMOTE'];

export const ROUND_TYPES = [
  'RECRUITER_CALL',
  'PHONE_SCREEN',
  'CODING_ASSESSMENT',
  'TECHNICAL',
  'SYSTEM_DESIGN',
  'HR',
  'FINAL',
  'ON_SITE',
];

export const INTERVIEW_OUTCOMES = ['PENDING', 'PASSED', 'FAILED', 'RESCHEDULED', 'CANCELLED'];

export const REMINDER_TYPES = [
  'APPLICATION_DEADLINE',
  'FOLLOW_UP',
  'INTERVIEW_PREP',
  'NEXT_ACTION',
  'CUSTOM',
];

export const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: '-deadline', label: 'Deadline (soonest)' },
  { value: 'applied_date', label: 'Recently applied' },
  { value: 'company', label: 'Company (A-Z)' },
  { value: 'title', label: 'Job title (A-Z)' },
  { value: 'status', label: 'Status' },
  { value: '-salary', label: 'Salary (high to low)' },
  { value: 'updated', label: 'Recently updated' },
];

export const PAGE_SIZES = [10, 20, 50, 100];

export const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD', 'CAD', 'AUD'];

/** A short list of common zones; the profile page also accepts any IANA id. */
export const TIMEZONES = [
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Australia/Sydney',
  'Europe/London',
  'Europe/Berlin',
  'Africa/Nairobi',
  'America/New_York',
  'America/Chicago',
  'America/Los_Angeles',
  'America/Toronto',
  'UTC',
];

export const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: 'chart' },
  { to: '/applications', label: 'Applications', icon: 'briefcase' },
  { to: '/interviews', label: 'Interviews', icon: 'video' },
  { to: '/reminders', label: 'Reminders', icon: 'bell' },
  { to: '/profile', label: 'Profile', icon: 'user' },
];

export const CHART_COLORS = {
  applications: '#4f46e5',
  interviews: '#0ea5e9',
  responses: '#10b981',
  offers: '#f59e0b',
  rejections: '#ef4444',
  grid: '#e2e8f0',
  axis: '#64748b',
};
