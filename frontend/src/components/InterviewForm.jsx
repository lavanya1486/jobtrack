import { useEffect, useMemo, useState } from 'react';
import Modal from './Modal';
import { Field, SelectField, TextAreaField } from './ApplicationForm';
import { INTERVIEW_OUTCOMES, ROUND_TYPES } from '../utils/constants';
import { defaultInterviewSlot, fieldErrorMap, fromDateTimeInputValue, titleCase, toDateTimeInputValue } from '../utils/format';

const EMPTY = {
  roundType: 'PHONE_SCREEN',
  scheduledAt: '',
  durationMinutes: 45,
  locationOrLink: '',
  interviewerName: '',
  prepNotes: '',
  feedback: '',
  outcome: 'PENDING',
};

export function fromInterview(interview) {
  if (!interview) return { ...EMPTY, scheduledAt: toDateTimeInputValue(defaultInterviewSlot()) };
  return {
    roundType: interview.roundType || EMPTY.roundType,
    scheduledAt: toDateTimeInputValue(interview.scheduledAt) || toDateTimeInputValue(defaultInterviewSlot()),
    durationMinutes: interview.durationMinutes ?? 45,
    locationOrLink: interview.locationOrLink || '',
    interviewerName: interview.interviewerName || '',
    prepNotes: interview.prepNotes || '',
    feedback: interview.feedback || '',
    outcome: interview.outcome || 'PENDING',
  };
}

export function toInterviewPayload(form) {
  return {
    roundType: form.roundType,
    scheduledAt: fromDateTimeInputValue(form.scheduledAt),
    durationMinutes: Number(form.durationMinutes) || 45,
    locationOrLink: form.locationOrLink.trim() || null,
    interviewerName: form.interviewerName.trim() || null,
    prepNotes: form.prepNotes.trim() || null,
    feedback: form.feedback.trim() || null,
    outcome: form.outcome,
  };
}

/** Tolerates a partially-filled form: an undefined optional field must not throw. */
export function validateInterviewForm(form = {}) {
  const errors = {};
  const link = (form.locationOrLink || '').trim();

  if (!form.roundType) errors.roundType = 'Round type is required';
  if (!form.scheduledAt) errors.scheduledAt = 'Pick a date and time';
  else if (!fromDateTimeInputValue(form.scheduledAt)) errors.scheduledAt = 'Invalid date/time';

  const duration = Number(form.durationMinutes);
  if (!Number.isFinite(duration) || duration < 5) errors.durationMinutes = 'At least 5 minutes';
  else if (duration > 720) errors.durationMinutes = 'At most 12 hours';

  if (link && !/^https?:\/\/.+\..+/.test(link)) {
    errors.locationOrLink = 'Enter a valid http(s) meeting link';
  }
  return errors;
}

/**
 * Schedule / reschedule / score an interview round.
 *
 * The same dialog is used for creating and editing: on create the outcome stays PENDING, on edit the
 * recruiter's feedback and outcome can be recorded without leaving the page.
 */
export default function InterviewForm({ open, interview, contextLabel, onClose, onSubmit, submitting, error }) {
  const [form, setForm] = useState(() => fromInterview(interview));
  const [touched, setTouched] = useState({});
  const serverErrors = useMemo(() => fieldErrorMap(error?.fieldErrors), [error]);

  useEffect(() => {
    if (open) {
      setForm(fromInterview(interview));
      setTouched({});
    }
  }, [open, interview]);

  const errors = validateInterviewForm(form);
  const hasErrors = Object.keys(errors).length > 0;
  const isEdit = Boolean(interview?.id);

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function showError(field) {
    return (touched[field] || touched.__submitted) && (errors[field] || serverErrors[field]);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setTouched((current) => ({ ...current, __submitted: true }));
    if (hasErrors) {
      document.getElementById(`field-${Object.keys(errors)[0]}`)?.focus();
      return;
    }
    await onSubmit(toInterviewPayload(form));
  }

  return (
    <Modal
      open={open}
      onClose={submitting ? undefined : onClose}
      title={isEdit ? 'Edit interview round' : 'Schedule an interview round'}
      description={contextLabel}
      size="md"
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" form="interview-form" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Saving…' : isEdit ? 'Save round' : 'Schedule round'}
          </button>
        </>
      }
    >
      <form id="interview-form" className="form" onSubmit={handleSubmit} noValidate>
        {error && !error.fieldErrors?.length ? (
          <p className="form__banner" role="alert">{error.message}</p>
        ) : null}

        <div className="form__grid form__grid--two">
          <SelectField
            id="roundType"
            label="Round type"
            value={form.roundType}
            onChange={(value) => update('roundType', value)}
            options={ROUND_TYPES.map((type) => ({ value: type, label: titleCase(type) }))}
          />
          <SelectField
            id="outcome"
            label="Outcome"
            value={form.outcome}
            onChange={(value) => update('outcome', value)}
            options={INTERVIEW_OUTCOMES.map((outcome) => ({ value: outcome, label: titleCase(outcome) }))}
            hint={isEdit ? 'Recording an outcome keeps your funnel accurate.' : undefined}
          />

          <Field
            id="scheduledAt"
            label="Date and time"
            type="datetime-local"
            value={form.scheduledAt}
            onChange={(value) => update('scheduledAt', value)}
            onBlur={() => setTouched((t) => ({ ...t, scheduledAt: true }))}
            error={showError('scheduledAt')}
            hint="Stored as UTC, displayed in your profile time zone."
          />
          <Field
            id="durationMinutes"
            label="Duration (minutes)"
            type="number"
            min="5"
            max="720"
            step="5"
            value={form.durationMinutes}
            onChange={(value) => update('durationMinutes', value)}
            onBlur={() => setTouched((t) => ({ ...t, durationMinutes: true }))}
            error={showError('durationMinutes')}
          />

          <Field
            id="interviewerName"
            label="Interviewer"
            value={form.interviewerName}
            onChange={(value) => update('interviewerName', value)}
            placeholder="Name or panel"
          />
          <Field
            id="locationOrLink"
            label="Meeting link / location"
            value={form.locationOrLink}
            onChange={(value) => update('locationOrLink', value)}
            onBlur={() => setTouched((t) => ({ ...t, locationOrLink: true }))}
            error={showError('locationOrLink')}
            placeholder="https://meet.google.com/…"
          />

          <TextAreaField
            id="prepNotes"
            label="Preparation notes"
            rows={3}
            value={form.prepNotes}
            onChange={(value) => update('prepNotes', value)}
            placeholder="Topics to revise, questions to ask, project talking points…"
          />
          <TextAreaField
            id="feedback"
            label="Feedback after the round"
            rows={3}
            value={form.feedback}
            onChange={(value) => update('feedback', value)}
            placeholder="What went well, what to improve, what they said…"
          />
        </div>
      </form>
    </Modal>
  );
}
