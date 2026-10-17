import { useEffect, useMemo, useState } from 'react';
import Modal from './Modal';
import { CURRENCIES, EMPLOYMENT_TYPES, STATUSES, STATUS_META, WORK_MODES } from '../utils/constants';
import { fieldErrorMap, titleCase, todayInputValue } from '../utils/format';

const EMPTY_FORM = {
  company: '',
  jobTitle: '',
  status: 'SAVED',
  employmentType: 'FULL_TIME',
  workMode: 'ONSITE',
  location: '',
  source: '',
  jobUrl: '',
  currency: 'INR',
  expectedSalaryMin: '',
  expectedSalaryMax: '',
  appliedAt: '',
  deadline: '',
  nextActionDate: '',
  nextAction: '',
  tags: '',
  jobDescription: '',
  notes: '',
};

/** Server-side field errors use camelCase names that map 1:1 onto these inputs. */
export function fromApplication(application) {
  if (!application) return { ...EMPTY_FORM, appliedAt: todayInputValue() };
  return {
    company: application.company || '',
    jobTitle: application.jobTitle || '',
    status: application.status || 'SAVED',
    employmentType: application.employmentType || 'FULL_TIME',
    workMode: application.workMode || 'ONSITE',
    location: application.location || '',
    source: application.source || '',
    jobUrl: application.jobUrl || '',
    currency: application.currency || 'INR',
    expectedSalaryMin: application.expectedSalaryMin ?? '',
    expectedSalaryMax: application.expectedSalaryMax ?? '',
    appliedAt: application.appliedAt || '',
    deadline: application.deadline || '',
    nextActionDate: application.nextActionDate || '',
    nextAction: application.nextAction || '',
    tags: (application.tags || []).join(', '),
    jobDescription: application.jobDescription || '',
    notes: application.notes || '',
  };
}

/** Empty strings must become null/absent, numbers must be numbers, tags must be an array. */
export function toPayload(form = {}) {
  const payload = {
    company: (form.company || '').trim(),
    jobTitle: (form.jobTitle || '').trim(),
    status: form.status,
    employmentType: form.employmentType,
    workMode: form.workMode,
    location: (form.location || '').trim() || null,
    source: (form.source || '').trim() || null,
    jobUrl: (form.jobUrl || '').trim() || null,
    currency: form.currency || 'INR',
    expectedSalaryMin: form.expectedSalaryMin === '' ? null : Number(form.expectedSalaryMin),
    expectedSalaryMax: form.expectedSalaryMax === '' ? null : Number(form.expectedSalaryMax),
    appliedAt: form.appliedAt || null,
    deadline: form.deadline || null,
    nextActionDate: form.nextActionDate || null,
    nextAction: (form.nextAction || '').trim() || null,
    jobDescription: (form.jobDescription || '').trim() || null,
    notes: (form.notes || '').trim() || null,
    tags: (form.tags || '')
      .split(',')
      .map((tag) => tag.trim().toLowerCase())
      .filter(Boolean)
      .slice(0, 15),
  };
  return payload;
}

/**
 * Client-side validation mirrors the Jakarta Bean Validation rules on the server.
 * It exists to give instant feedback - the server remains the source of truth.
 */
export function validateApplicationForm(form = {}) {
  const errors = {};
  const company = (form.company || '').trim();
  const jobTitle = (form.jobTitle || '').trim();
  const jobUrl = (form.jobUrl || '').trim();
  const nextAction = (form.nextAction || '').trim();
  const tags = (form.tags || '').split(',').map((tag) => tag.trim()).filter(Boolean);

  if (!company) errors.company = 'Company is required';
  else if (company.length > 140) errors.company = 'At most 140 characters';

  if (!jobTitle) errors.jobTitle = 'Job title is required';
  else if (jobTitle.length > 160) errors.jobTitle = 'At most 160 characters';

  if (jobUrl && !/^https?:\/\/.+\..+/.test(jobUrl)) {
    errors.jobUrl = 'Enter a valid http(s) URL';
  }

  const min = form.expectedSalaryMin === '' ? null : Number(form.expectedSalaryMin);
  const max = form.expectedSalaryMax === '' ? null : Number(form.expectedSalaryMax);
  if (min !== null && Number.isNaN(min)) errors.expectedSalaryMin = 'Must be a number';
  if (max !== null && Number.isNaN(max)) errors.expectedSalaryMax = 'Must be a number';
  if (min !== null && min < 0) errors.expectedSalaryMin = 'Cannot be negative';
  if (max !== null && max < 0) errors.expectedSalaryMax = 'Cannot be negative';
  if (min !== null && max !== null && !Number.isNaN(min) && !Number.isNaN(max) && max < min) {
    errors.expectedSalaryMax = 'Maximum must be greater than or equal to the minimum';
  }

  if (form.deadline && form.appliedAt && form.deadline < form.appliedAt) {
    errors.deadline = 'Deadline cannot be before the application date';
  }
  if (form.nextActionDate && form.appliedAt && form.nextActionDate < form.appliedAt) {
    errors.nextActionDate = 'Next action cannot be before the application date';
  }

  if (tags.length > 15) errors.tags = 'At most 15 tags';
  if (tags.some((tag) => tag.length > 40)) errors.tags = 'Each tag must be 40 characters or fewer';

  if ((form.notes || '').length > 5000) errors.notes = 'At most 5000 characters';
  if ((form.jobDescription || '').length > 8000) errors.jobDescription = 'At most 8000 characters';
  if (nextAction.length > 200) errors.nextAction = 'At most 200 characters';

  return errors;
}

/**
 * Create / edit application dialog.
 *
 * Server validation errors are merged into the same inline error slots, so a rule the client missed
 * (or a race such as a duplicate) still shows up next to the right field.
 */
export default function ApplicationForm({ open, application, onClose, onSubmit, submitting, error }) {
  const [form, setForm] = useState(() => fromApplication(application));
  const [touched, setTouched] = useState({});
  const serverErrors = useMemo(() => fieldErrorMap(error?.fieldErrors), [error]);

  useEffect(() => {
    if (open) {
      setForm(fromApplication(application));
      setTouched({});
    }
  }, [open, application]);

  const errors = useMemo(() => validateApplicationForm(form), [form]);
  const hasErrors = Object.keys(errors).length > 0;

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function blur(field) {
    setTouched((current) => ({ ...current, [field]: true }));
  }

  function showError(field) {
    return (touched[field] || touched.__submitted) && (errors[field] || serverErrors[field]);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setTouched((current) => ({ ...current, __submitted: true }));
    if (hasErrors) {
      const firstField = Object.keys(errors)[0];
      document.getElementById(`field-${firstField}`)?.focus();
      return;
    }
    await onSubmit(toPayload(form));
  }

  const isEdit = Boolean(application?.id);

  return (
    <Modal
      open={open}
      onClose={submitting ? undefined : onClose}
      size="lg"
      title={isEdit ? 'Edit application' : 'Track a new application'}
      description={
        isEdit
          ? 'Changes are saved immediately and status changes are written to the audit trail.'
          : 'Everything except company and job title is optional - you can fill in the details later.'
      }
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" form="application-form" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Add application'}
          </button>
        </>
      }
    >
      <form id="application-form" className="form" onSubmit={handleSubmit} noValidate>
        {error && !error.fieldErrors?.length ? (
          <p className="form__banner" role="alert">
            {error.message}
          </p>
        ) : null}

        <div className="form__grid">
          <Field
            id="company"
            label="Company"
            required
            error={showError('company')}
            value={form.company}
            onChange={(value) => update('company', value)}
            onBlur={() => blur('company')}
            placeholder="e.g. Zoho"
            autoComplete="organization"
          />
          <Field
            id="jobTitle"
            label="Job title"
            required
            error={showError('jobTitle')}
            value={form.jobTitle}
            onChange={(value) => update('jobTitle', value)}
            onBlur={() => blur('jobTitle')}
            placeholder="e.g. Java Developer Intern"
          />

          <SelectField
            id="status"
            label="Status"
            value={form.status}
            onChange={(value) => update('status', value)}
            options={STATUSES.map((status) => ({ value: status, label: STATUS_META[status].label }))}
            hint={isEdit ? 'Impossible transitions are rejected by the API.' : 'Start as Saved if you have not applied yet.'}
          />
          <SelectField
            id="employmentType"
            label="Employment type"
            value={form.employmentType}
            onChange={(value) => update('employmentType', value)}
            options={EMPLOYMENT_TYPES.map((type) => ({ value: type, label: titleCase(type) }))}
          />
          <SelectField
            id="workMode"
            label="Work mode"
            value={form.workMode}
            onChange={(value) => update('workMode', value)}
            options={WORK_MODES.map((mode) => ({ value: mode, label: titleCase(mode) }))}
          />
          <Field
            id="location"
            label="Location"
            value={form.location}
            onChange={(value) => update('location', value)}
            onBlur={() => blur('location')}
            placeholder="e.g. Bengaluru, India"
          />

          <Field
            id="source"
            label="Source"
            value={form.source}
            onChange={(value) => update('source', value)}
            onBlur={() => blur('source')}
            placeholder="LinkedIn, referral, career page…"
          />
          <Field
            id="jobUrl"
            label="Job posting URL"
            type="url"
            value={form.jobUrl}
            onChange={(value) => update('jobUrl', value)}
            onBlur={() => blur('jobUrl')}
            error={showError('jobUrl')}
            placeholder="https://…"
            className="span-2"
          />

          <SelectField
            id="currency"
            label="Currency"
            value={form.currency}
            onChange={(value) => update('currency', value)}
            options={CURRENCIES.map((currency) => ({ value: currency, label: currency }))}
          />
          <Field
            id="expectedSalaryMin"
            label="Expected salary (min)"
            type="number"
            min="0"
            step="1000"
            value={form.expectedSalaryMin}
            onChange={(value) => update('expectedSalaryMin', value)}
            onBlur={() => blur('expectedSalaryMin')}
            error={showError('expectedSalaryMin')}
          />
          <Field
            id="expectedSalaryMax"
            label="Expected salary (max)"
            type="number"
            min="0"
            step="1000"
            value={form.expectedSalaryMax}
            onChange={(value) => update('expectedSalaryMax', value)}
            onBlur={() => blur('expectedSalaryMax')}
            error={showError('expectedSalaryMax')}
          />

          <Field
            id="appliedAt"
            label="Applied on"
            type="date"
            value={form.appliedAt}
            onChange={(value) => update('appliedAt', value)}
            onBlur={() => blur('appliedAt')}
          />
          <Field
            id="deadline"
            label="Deadline"
            type="date"
            value={form.deadline}
            onChange={(value) => update('deadline', value)}
            onBlur={() => blur('deadline')}
            error={showError('deadline')}
            hint="Generates a reminder automatically."
          />
          <Field
            id="nextActionDate"
            label="Next action date"
            type="date"
            value={form.nextActionDate}
            onChange={(value) => update('nextActionDate', value)}
            onBlur={() => blur('nextActionDate')}
            error={showError('nextActionDate')}
          />
          <Field
            id="nextAction"
            label="Next action"
            value={form.nextAction}
            onChange={(value) => update('nextAction', value)}
            onBlur={() => blur('nextAction')}
            placeholder="e.g. Follow up with the recruiter"
          />

          <Field
            id="tags"
            label="Tags"
            value={form.tags}
            onChange={(value) => update('tags', value)}
            onBlur={() => blur('tags')}
            error={showError('tags')}
            placeholder="referral, spring-boot, remote (comma separated)"
            hint="Used for filtering; stored lower-case."
            className="span-2"
          />

          <TextAreaField
            id="jobDescription"
            label="Job description"
            value={form.jobDescription}
            onChange={(value) => update('jobDescription', value)}
            onBlur={() => blur('jobDescription')}
            error={showError('jobDescription')}
            rows={4}
          />
          <TextAreaField
            id="notes"
            label="Notes"
            value={form.notes}
            onChange={(value) => update('notes', value)}
            onBlur={() => blur('notes')}
            error={showError('notes')}
            rows={4}
            placeholder="Referrer name, prep status, salary discussion…"
          />
        </div>
      </form>
    </Modal>
  );
}

export function Field({ id, label, value, onChange, onBlur, error, hint, required, className, ...rest }) {
  return (
    <div className={`field ${className || ''}`}>
      <label className="field__label" htmlFor={`field-${id}`}>
        {label}
        {required ? <span className="field__required" aria-hidden="true"> *</span> : null}
      </label>
      <input
        id={`field-${id}`}
        name={id}
        className={`input${error ? ' input--invalid' : ''}`}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? `error-${id}` : hint ? `hint-${id}` : undefined}
        {...rest}
      />
      {error ? (
        <p className="field__error" id={`error-${id}`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="field__hint" id={`hint-${id}`}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function SelectField({ id, label, value, onChange, options, hint, className }) {
  return (
    <div className={`field ${className || ''}`}>
      <label className="field__label" htmlFor={`field-${id}`}>
        {label}
      </label>
      <select
        id={`field-${id}`}
        name={id}
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {hint ? <p className="field__hint">{hint}</p> : null}
    </div>
  );
}

export function TextAreaField({ id, label, value, onChange, onBlur, error, hint, rows = 3, className }) {
  return (
    <div className={`field ${className || ''}`}>
      <label className="field__label" htmlFor={`field-${id}`}>
        {label}
      </label>
      <textarea
        id={`field-${id}`}
        name={id}
        rows={rows}
        className={`input${error ? ' input--invalid' : ''}`}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        aria-invalid={error ? 'true' : undefined}
      />
      {error ? (
        <p className="field__error" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="field__hint">{hint}</p>
      ) : null}
    </div>
  );
}
