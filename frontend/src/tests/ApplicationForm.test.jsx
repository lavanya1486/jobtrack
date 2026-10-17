import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ApplicationForm from '../components/ApplicationForm';
import InterviewForm from '../components/InterviewForm';

function renderForm(props = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const onClose = vi.fn();
  render(
    <ApplicationForm
      open
      application={null}
      submitting={false}
      error={null}
      onClose={onClose}
      onSubmit={onSubmit}
      {...props}
    />,
  );
  return { onSubmit, onClose };
}

describe('application form', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('is closed when `open` is false', () => {
    render(<ApplicationForm open={false} application={null} onClose={vi.fn()} onSubmit={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows required-field errors without submitting', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.click(screen.getByRole('button', { name: /add application/i }));

    expect(await screen.findByText('Company is required')).toBeInTheDocument();
    expect(screen.getByText('Job title is required')).toBeInTheDocument();
    expect(screen.getByLabelText(/^company/i)).toHaveAttribute('aria-invalid', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a payload with trimmed text, parsed tags and nulls for empty fields', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText(/^company/i), '  Zoho  ');
    await user.type(screen.getByLabelText(/job title/i), 'Java Developer Intern');
    await user.type(screen.getByLabelText(/^location/i), 'Chennai');
    await user.type(screen.getByLabelText(/^tags/i), 'Referral, Spring Boot ,, remote');
    await user.type(screen.getByLabelText(/expected salary \(min\)/i), '600000');
    await user.selectOptions(screen.getByLabelText(/^status/i), 'APPLIED');
    await user.click(screen.getByRole('button', { name: /add application/i }));

    await waitForOnSubmit(onSubmit);
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.company).toBe('Zoho');
    expect(payload.jobTitle).toBe('Java Developer Intern');
    expect(payload.status).toBe('APPLIED');
    expect(payload.tags).toEqual(['referral', 'spring boot', 'remote']);
    expect(payload.expectedSalaryMin).toBe(600000);
    expect(payload.expectedSalaryMax).toBeNull();
    expect(payload.notes).toBeNull();
    expect(payload.jobUrl).toBeNull();
  });

  it('refuses a salary range that is inverted', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText(/^company/i), 'Zoho');
    await user.type(screen.getByLabelText(/job title/i), 'Engineer');
    await user.type(screen.getByLabelText(/expected salary \(min\)/i), '900000');
    await user.type(screen.getByLabelText(/expected salary \(max\)/i), '600000');
    await user.click(screen.getByRole('button', { name: /add application/i }));

    expect(
      await screen.findByText('Maximum must be greater than or equal to the minimum'),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('renders server-side validation errors next to the right field', async () => {
    const user = userEvent.setup();
    renderForm({
      error: Object.assign(new Error('Request validation failed'), {
        fieldErrors: [{ field: 'jobUrl', message: 'must be a valid URL' }],
      }),
    });

    await user.type(screen.getByLabelText(/^company/i), 'Zoho');
    await user.type(screen.getByLabelText(/job title/i), 'Engineer');
    await user.click(screen.getByRole('button', { name: /add application/i }));

    expect(await screen.findByText('must be a valid URL')).toBeInTheDocument();
  });

  it('shows a form-wide banner when the server rejects the request without field errors', () => {
    renderForm({ error: Object.assign(new Error('Salary range is invalid'), { fieldErrors: [] }) });
    expect(screen.getByRole('alert')).toHaveTextContent('Salary range is invalid');
  });

  it('prefills every field when editing an existing application', () => {
    renderForm({
      application: {
        id: 7,
        company: 'Atlassian',
        jobTitle: 'Backend Engineer',
        status: 'INTERVIEW',
        location: 'Bengaluru',
        currency: 'INR',
        expectedSalaryMin: 1800000,
        expectedSalaryMax: 2400000,
        deadline: '2026-11-30',
        tags: ['referral', 'system-design'],
        notes: 'Round 2 pending',
      },
    });

    expect(screen.getByRole('dialog')).toHaveAccessibleName('Edit application');
    expect(screen.getByLabelText(/^company/i)).toHaveValue('Atlassian');
    expect(screen.getByLabelText(/^status/i)).toHaveValue('INTERVIEW');
    expect(screen.getByLabelText(/expected salary \(min\)/i)).toHaveValue(1800000);
    expect(screen.getByLabelText(/^deadline/i)).toHaveValue('2026-11-30');
    expect(screen.getByLabelText(/^tags/i)).toHaveValue('referral, system-design');
    expect(screen.getByLabelText(/^notes/i)).toHaveValue('Round 2 pending');
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
  });

  it('closes when the user cancels', async () => {
    const user = userEvent.setup();
    const { onClose } = renderForm();

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe('interview form', () => {
  it('requires a scheduled time and blocks the submit', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <InterviewForm open interview={null} onClose={vi.fn()} onSubmit={onSubmit} submitting={false} error={null} />,
    );

    const scheduled = screen.getByLabelText(/date and time/i);
    await user.clear(scheduled);
    await user.click(screen.getByRole('button', { name: /schedule round/i }));

    expect(await screen.findByText('Pick a date and time')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('defaults to tomorrow at 10:30 in the local time zone', () => {
    render(
      <InterviewForm open interview={null} onClose={vi.fn()} onSubmit={vi.fn()} submitting={false} error={null} />,
    );
    const value = screen.getByLabelText(/date and time/i).value;
    expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T10:30$/);
  });

  it('prefills and offers outcome capture when editing a round', () => {
    render(
      <InterviewForm
        open
        interview={{
          id: 3,
          roundType: 'TECHNICAL',
          scheduledAt: '2026-05-20T04:30:00.000Z',
          durationMinutes: 60,
          interviewerName: 'Priya Nair',
          locationOrLink: 'https://meet.example.com/abc',
          outcome: 'PASSED',
          feedback: 'Strong on JPA',
        }}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        submitting={false}
        error={null}
      />,
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('Edit interview round');
    expect(within(dialog).getByLabelText(/round type/i)).toHaveValue('TECHNICAL');
    expect(within(dialog).getByLabelText(/^interviewer/i)).toHaveValue('Priya Nair');
    expect(within(dialog).getByLabelText(/^outcome/i)).toHaveValue('PASSED');
    expect(within(dialog).getByLabelText(/feedback/i)).toHaveValue('Strong on JPA');
    expect(within(dialog).getByRole('button', { name: /save round/i })).toBeInTheDocument();
  });
});

/** userEvent + portals: wait for the submit handler to run rather than asserting synchronously. */
async function waitForOnSubmit(spy) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (spy.mock.calls.length > 0) return;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  throw new Error('onSubmit was never called');
}
