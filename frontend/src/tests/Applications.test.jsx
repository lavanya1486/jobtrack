import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { applicationsApi } from '../api/endpoints';
import { ToastProvider } from '../components/Toast';
import Applications from '../pages/Applications';

vi.mock('../api/endpoints', () => ({
  applicationsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    changeStatus: vi.fn(),
    remove: vi.fn(),
    filterOptions: vi.fn(),
    addInterview: vi.fn(),
  },
}));

const ROWS = [
  {
    id: 1,
    company: 'Zoho',
    jobTitle: 'Java Developer Intern',
    status: 'APPLIED',
    employmentType: 'FULL_TIME',
    workMode: 'REMOTE',
    location: 'Chennai',
    appliedAt: '2026-09-01',
    deadline: '2026-11-30',
    daysUntilDeadline: 44,
    daysSinceApplied: 46,
    deadlineOverdue: false,
    currency: 'INR',
    expectedSalaryMin: 600000,
    expectedSalaryMax: 900000,
    tags: ['referral'],
    nextAction: 'Follow up',
    nextActionDate: '2026-10-20',
    nextActionDue: false,
    submitted: true,
    interviewCount: 0,
  },
  {
    id: 2,
    company: 'Atlassian',
    jobTitle: 'Backend Engineer',
    status: 'INTERVIEW',
    employmentType: 'FULL_TIME',
    workMode: 'HYBRID',
    location: 'Bengaluru',
    appliedAt: '2026-08-12',
    deadline: null,
    daysUntilDeadline: null,
    daysSinceApplied: 66,
    deadlineOverdue: false,
    currency: 'INR',
    expectedSalaryMin: null,
    expectedSalaryMax: 2400000,
    tags: [],
    nextAction: null,
    nextActionDate: null,
    submitted: true,
    interviewCount: 2,
  },
];

function page(content = ROWS, overrides = {}) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <ToastProvider>
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <Applications />
      </MemoryRouter>
    </ToastProvider>,
  );
}

describe('applications page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    applicationsApi.list.mockResolvedValue(page());
    applicationsApi.filterOptions.mockResolvedValue({
      statuses: ['SAVED', 'APPLIED'],
      employmentTypes: ['FULL_TIME'],
      workModes: ['REMOTE'],
      tags: ['referral', 'spring-boot'],
      locations: ['Chennai', 'Bengaluru'],
      companies: ['Zoho', 'Atlassian'],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('asks the server for the first page and renders the rows it returns', async () => {
    renderPage();

    expect(await screen.findByText('Java Developer Intern')).toBeInTheDocument();
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(applicationsApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, sort: 'newest' }),
    );
    expect(screen.getByText(/2 records matching your filters/i)).toBeInTheDocument();
  });

  it('shows a skeleton while loading and never renders a stale table', async () => {
    let resolveList;
    applicationsApi.list.mockReturnValue(
      new Promise((resolve) => {
        resolveList = resolve;
      }),
    );

    renderPage();
    expect(screen.queryByText('Java Developer Intern')).not.toBeInTheDocument();

    resolveList(page());
    expect(await screen.findByText('Java Developer Intern')).toBeInTheDocument();
  });

  it('debounces the search box: no request per keystroke, one settled request', async () => {
    vi.useFakeTimers();
    renderPage();
    await vi.advanceTimersByTimeAsync(0);
    const callsAfterLoad = applicationsApi.list.mock.calls.length;
    expect(callsAfterLoad).toBe(1);

    const search = screen.getByLabelText(/search by company/i);
    fireEvent.change(search, { target: { value: 'z' } });
    fireEvent.change(search, { target: { value: 'zo' } });
    fireEvent.change(search, { target: { value: 'zoho' } });
    await vi.advanceTimersByTimeAsync(100);
    expect(applicationsApi.list).toHaveBeenCalledTimes(callsAfterLoad);

    await vi.advanceTimersByTimeAsync(400);
    expect(applicationsApi.list).toHaveBeenCalledTimes(callsAfterLoad + 1);
    expect(applicationsApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'zoho', page: 0 }),
    );
  });

  it('toggles status chips into a multi-value filter and resets to page 0', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    await user.click(screen.getByRole('button', { name: /^applied$/i }));
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: ['APPLIED'] }),
      ),
    );

    await user.click(screen.getByRole('button', { name: /^interview$/i }));
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: ['APPLIED', 'INTERVIEW'] }),
      ),
    );

    await user.click(screen.getByRole('button', { name: /^applied$/i }));
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: ['INTERVIEW'] }),
      ),
    );
  });

  it('sends the sort selection and the row size to the API', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    await user.selectOptions(screen.getByLabelText(/sort applications/i), 'company');
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'company' })),
    );

    await user.selectOptions(screen.getByLabelText(/^rows$/i), '50');
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ size: 50, page: 0 })),
    );
  });

  it('counts active filters and clears them all at once', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    const clear = within(screen.getByRole('region', { name: /filter applications/i })).getByRole(
      'button',
      { name: /clear filters/i },
    );
    expect(clear).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /^offer$/i }));
    await waitFor(() => expect(clear).toBeEnabled());
    expect(clear).toHaveTextContent('Clear filters (1)');

    await user.click(clear);
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: [], sort: 'newest', page: 0 }),
      ),
    );
  });

  it('changes the status inline and refreshes the list', async () => {
    applicationsApi.changeStatus.mockResolvedValue({ id: 1, status: 'OFFER' });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    const callsBefore = applicationsApi.list.mock.calls.length;
    await user.selectOptions(screen.getByLabelText(/change status for zoho/i), 'OFFER');

    await waitFor(() =>
      expect(applicationsApi.changeStatus).toHaveBeenCalledWith(1, { status: 'OFFER' }),
    );
    await waitFor(() => expect(applicationsApi.list.mock.calls.length).toBeGreaterThan(callsBefore));
    expect(await screen.findByText(/zoho: offer/i)).toBeInTheDocument();
  });

  it('requires confirmation before deleting, then calls the API', async () => {
    applicationsApi.remove.mockResolvedValue({});
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0]);

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('Delete this application?');
    expect(within(dialog).getByText(/permanently removed/i)).toBeInTheDocument();
    expect(applicationsApi.remove).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }));

    await waitFor(() => expect(applicationsApi.remove).toHaveBeenCalledWith(1));
  });

  it('opens the create dialog and posts the new application', async () => {
    applicationsApi.create.mockResolvedValue({ id: 3, company: 'Freshworks', jobTitle: 'SRE' });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    await user.click(screen.getByRole('button', { name: /track application/i }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('Track a new application');

    await user.type(within(dialog).getByLabelText(/^company/i), 'Freshworks');
    await user.type(within(dialog).getByLabelText(/job title/i), 'Site Reliability Engineer');
    await user.click(within(dialog).getByRole('button', { name: /add application/i }));

    await waitFor(() =>
      expect(applicationsApi.create).toHaveBeenCalledWith(
        expect.objectContaining({ company: 'Freshworks', jobTitle: 'Site Reliability Engineer' }),
      ),
    );
    expect(await screen.findByText(/added to your tracker/i)).toBeInTheDocument();
  });

  it('shows an actionable empty state when nothing matches the filters', async () => {
    applicationsApi.list.mockResolvedValue(page([]));
    const user = userEvent.setup();
    renderPage();

    // No filters yet: the copy invites the user to add their first application.
    expect(await screen.findByText('No applications yet')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^saved$/i }));

    expect(
      await screen.findByText('No applications match these filters'),
    ).toBeInTheDocument();
    const filterBar = screen.getByRole('region', { name: /filter applications/i });
    expect(within(filterBar).getByRole('button', { name: /clear filters/i })).toBeEnabled();
    // The empty state offers the same escape hatch.
    await user.click(screen.getByRole('button', { name: /^clear filters$/i }));
    expect(await screen.findByText('No applications yet')).toBeInTheDocument();
  });

  it('explains how to start when the user has no applications at all', async () => {
    applicationsApi.list.mockResolvedValue(page([]));
    renderPage();

    expect(await screen.findByText('No applications yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add your first application/i })).toBeInTheDocument();
  });

  it('surfaces an API failure with a retry action', async () => {
    applicationsApi.list
      .mockRejectedValueOnce(Object.assign(new Error('Service unavailable'), { status: 503 }))
      .mockResolvedValue(page());
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Service unavailable')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('Java Developer Intern')).toBeInTheDocument();
  });

  it('steps through pages using the server-provided totals', async () => {
    applicationsApi.list.mockResolvedValue(
      page(ROWS, { totalElements: 45, totalPages: 3, last: false }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Java Developer Intern');

    expect(screen.getByText(/showing/i)).toHaveTextContent('of 45');

    await user.click(screen.getByRole('button', { name: 'Page 2' }));
    await waitFor(() =>
      expect(applicationsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })),
    );
  });
});
