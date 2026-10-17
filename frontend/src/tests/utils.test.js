import { describe, expect, it } from 'vitest';
import { cleanParams } from '../api/endpoints';
import {
  formatCompactMoney,
  formatDate,
  formatNumber,
  formatPercent,
  formatSalaryRange,
  fieldErrorMap,
  fromDateTimeInputValue,
  initials,
  titleCase,
  toDateTimeInputValue,
} from '../utils/format';
import { validateApplicationForm, toPayload, fromApplication } from '../components/ApplicationForm';
import { validateInterviewForm, toInterviewPayload } from '../components/InterviewForm';
import { countActiveFilters } from '../components/FiltersBar';
import { passwordStrength } from '../pages/Register';
import { groupReminders, validateReminderForm } from '../pages/Reminders';
import { STATUS_TRANSITIONS, STATUSES } from '../utils/constants';

describe('formatting helpers', () => {
  it('formats rates as percentages with one decimal', () => {
    expect(formatPercent(0.5909)).toBe('59.1%');
    expect(formatPercent(0)).toBe('0.0%');
    expect(formatPercent(null)).toBe('—');
  });

  it('formats numbers with Indian digit grouping', () => {
    expect(formatNumber(1234567)).toBe('12,34,567');
    expect(formatNumber(0)).toBe('0');
    expect(formatNumber(undefined)).toBe('—');
  });

  it('compacts money per currency', () => {
    expect(formatCompactMoney(1200000, 'INR')).toBe('₹12.0L');
    expect(formatCompactMoney(15000000, 'INR')).toBe('₹1.5Cr');
    expect(formatCompactMoney(1500000, 'USD')).toBe('$1.5M');
    expect(formatCompactMoney(null, 'INR')).toBeNull();
  });

  it('renders a salary range, or a dash when nothing is set', () => {
    expect(formatSalaryRange(600000, 900000, 'INR')).toBe('₹6.0L – ₹9.0L');
    expect(formatSalaryRange(null, null, 'INR')).toBe('—');
    expect(formatSalaryRange(null, 900000, 'INR')).toBe('₹9.0L');
  });

  it('returns a placeholder for invalid dates instead of throwing', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('not-a-date')).toBe('—');
    expect(formatDate('2026-03-04')).toMatch(/Mar 2026/);
  });

  it('turns SNAKE_CASE enums into labels', () => {
    expect(titleCase('PHONE_SCREEN')).toBe('Phone Screen');
    expect(titleCase('FULL_TIME')).toBe('Full Time');
    expect(titleCase('')).toBe('');
  });

  it('round-trips datetime-local values through UTC', () => {
    const iso = '2026-05-20T04:30:00.000Z';
    const local = toDateTimeInputValue(iso);
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(fromDateTimeInputValue(local)).toBe(iso);
    expect(fromDateTimeInputValue('')).toBeNull();
  });

  it('builds initials and a field-error map', () => {
    expect(initials('Asha Verma')).toBe('AV');
    expect(initials('asha')).toBe('A');
    expect(
      fieldErrorMap([
        { field: 'company', message: 'Required' },
        { field: 'jobUrl', message: 'Invalid' },
      ]),
    ).toEqual({ company: 'Required', jobUrl: 'Invalid' });
  });
});

describe('application form validation (mirrors the Jakarta rules on the server)', () => {
  it('requires company and job title', () => {
    const errors = validateApplicationForm(fromApplication(null));
    expect(errors.company).toBeTruthy();
    expect(errors.jobTitle).toBeTruthy();
  });

  it('rejects a maximum salary below the minimum', () => {
    const errors = validateApplicationForm({
      ...fromApplication(null),
      company: 'Zoho',
      jobTitle: 'Engineer',
      expectedSalaryMin: '900000',
      expectedSalaryMax: '600000',
    });
    expect(errors.expectedSalaryMax).toBeTruthy();
  });

  it('rejects a deadline before the application date', () => {
    const errors = validateApplicationForm({
      ...fromApplication(null),
      company: 'Zoho',
      jobTitle: 'Engineer',
      appliedAt: '2026-05-10',
      deadline: '2026-05-01',
    });
    expect(errors.deadline).toBeTruthy();
  });

  it('rejects a malformed posting URL', () => {
    const errors = validateApplicationForm({
      ...fromApplication(null),
      company: 'Zoho',
      jobTitle: 'Engineer',
      jobUrl: 'zoho-dot-com',
    });
    expect(errors.jobUrl).toBeTruthy();
  });

  it('serialises the payload the way the API expects it', () => {
    const payload = toPayload({
      ...fromApplication(null),
      company: '  Zoho  ',
      jobTitle: 'Java Developer',
      location: '',
      tags: 'Referral, spring-boot ,, REMOTE',
      expectedSalaryMin: '600000',
      expectedSalaryMax: '',
      nextAction: '   ',
    });

    expect(payload.company).toBe('Zoho');
    expect(payload.location).toBeNull();
    expect(payload.nextAction).toBeNull();
    expect(payload.expectedSalaryMin).toBe(600000);
    expect(payload.expectedSalaryMax).toBeNull();
    expect(payload.tags).toEqual(['referral', 'spring-boot', 'remote']);
  });

  it('prefills every field when editing', () => {
    const form = fromApplication({
      company: 'Zoho',
      jobTitle: 'Java Developer',
      status: 'INTERVIEW',
      tags: ['referral'],
      expectedSalaryMax: 900000,
      deadline: '2026-11-01',
    });
    expect(form.status).toBe('INTERVIEW');
    expect(form.tags).toBe('referral');
    expect(form.expectedSalaryMax).toBe(900000);
    expect(form.deadline).toBe('2026-11-01');
  });
});

describe('interview form validation', () => {
  it('requires a scheduled time and a sane duration', () => {
    const errors = validateInterviewForm({ roundType: 'TECHNICAL', scheduledAt: '', durationMinutes: 2 });
    expect(errors.scheduledAt).toBeTruthy();
    expect(errors.durationMinutes).toBeTruthy();
  });

  it('sends an ISO instant and numeric duration', () => {
    const payload = toInterviewPayload({
      roundType: 'PHONE_SCREEN',
      scheduledAt: '2026-05-20T10:30',
      durationMinutes: '45',
      locationOrLink: ' https://meet.example.com/abc ',
      interviewerName: '',
      prepNotes: '',
      feedback: '',
      outcome: 'PENDING',
    });
    expect(payload.scheduledAt).toMatch(/^2026-05-20T.*Z$/);
    expect(payload.durationMinutes).toBe(45);
    expect(payload.locationOrLink).toBe('https://meet.example.com/abc');
    expect(payload.interviewerName).toBeNull();
  });
});

describe('reminder helpers', () => {
  it('validates the manual reminder form', () => {
    expect(validateReminderForm({ title: 'ab', dueAt: '' })).toEqual(
      expect.objectContaining({ title: expect.any(String), dueAt: expect.any(String) }),
    );
    expect(
      validateReminderForm({ title: 'Follow up', dueAt: '2026-05-20T10:30', details: '' }),
    ).toEqual({});
  });

  it('buckets reminders into overdue / today / later / completed', () => {
    const now = Date.now();
    const groups = groupReminders([
      { id: 1, title: 'late', dueAt: new Date(now - 3600_000).toISOString(), completed: false },
      { id: 2, title: 'soon', dueAt: new Date(now + 3600_000).toISOString(), completed: false },
      { id: 3, title: 'far', dueAt: new Date(now + 20 * 86400_000).toISOString(), completed: false },
      { id: 4, title: 'done', dueAt: new Date(now - 1000).toISOString(), completed: true },
    ]);

    expect(groups.Overdue.map((r) => r.id)).toEqual([1]);
    expect(groups.Completed.map((r) => r.id)).toEqual([4]);
    expect(groups.Later.map((r) => r.id)).toEqual([3]);
    expect([...groups.Today, ...groups.Tomorrow, ...groups['This week']].map((r) => r.id)).toEqual([2]);
  });
});

describe('filter state', () => {
  it('counts only the filters that are actually set', () => {
    expect(countActiveFilters({}, 'newest')).toBe(0);
    expect(countActiveFilters({ status: ['APPLIED'], search: 'zoho' }, 'newest')).toBe(2);
    expect(countActiveFilters({}, 'company')).toBe(1);
    expect(countActiveFilters({ status: [] }, 'newest')).toBe(0);
  });
});

describe('password strength meter', () => {
  it('grades passwords without ever accepting a weak one as strong', () => {
    expect(passwordStrength('').level).toBe('empty');
    expect(passwordStrength('abc').level).toBe('weak');
    expect(passwordStrength('Password1').level).not.toBe('weak');
    expect(passwordStrength('Str0ng!Passphrase').level).toBe('strong');
  });
});

describe('status transition matrix (kept in sync with ApplicationStatusPolicy)', () => {
  it('covers every status and never allows a no-op transition', () => {
    expect(Object.keys(STATUS_TRANSITIONS).sort()).toEqual([...STATUSES].sort());
    STATUSES.forEach((status) => {
      expect(STATUS_TRANSITIONS[status]).not.toContain(status);
      STATUS_TRANSITIONS[status].forEach((target) => expect(STATUSES).toContain(target));
    });
  });

  it('keeps drafts out of the funnel: SAVED can only be applied or withdrawn', () => {
    expect(STATUS_TRANSITIONS.SAVED).toEqual(['APPLIED', 'WITHDRAWN']);
  });

  it('lets a rejected application be reopened', () => {
    expect(STATUS_TRANSITIONS.REJECTED).toContain('APPLIED');
  });
});

describe('cleanParams', () => {
  it('drops empty values so Spring never binds an empty string', () => {
    expect(
      cleanParams({ search: '', status: [], location: null, tag: undefined, page: 0, size: 20 }),
    ).toEqual({ page: 0, size: 20 });
  });

  it('keeps multi-value filters as arrays', () => {
    expect(cleanParams({ status: ['APPLIED', 'OFFER', ''] })).toEqual({
      status: ['APPLIED', 'OFFER'],
    });
  });
});
