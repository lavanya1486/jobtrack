import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { analyticsApi, interviewsApi, userApi } from '../api/endpoints';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../components/Toast';
import Dashboard from '../pages/Dashboard';

vi.mock('../api/endpoints', () => ({
  analyticsApi: { summary: vi.fn(), trends: vi.fn(), upcoming: vi.fn() },
  interviewsApi: { upcoming: vi.fn(), list: vi.fn() },
  userApi: { me: vi.fn() },
  remindersApi: { list: vi.fn().mockResolvedValue({ totalElements: 0 }) },
}));

/** Mirrors the shape of AnalyticsSummaryResponse produced by the seeded demo account. */
const SUMMARY = {
  totalApplications: 24,
  submittedApplications: 22,
  savedDrafts: 2,
  activeApplications: 9,
  closedApplications: 13,
  respondedApplications: 13,
  reachedInterview: 8,
  offersReceived: 2,
  rejections: 9,
  withdrawals: 2,
  interviewsScheduled: 21,
  upcomingInterviews: 2,
  completedInterviews: 19,
  passedInterviews: 8,
  failedInterviews: 11,
  overdueApplications: 1,
  dueNextActions: 3,
  openReminders: 11,
  overdueReminders: 2,
  averageDaysToFirstResponse: 7.8,
  responseRate: 0.5909,
  interviewRate: 0.3636,
  offerRate: 0.0909,
  averageExpectedSalaryMax: 1500000,
  statusDistribution: [
    { status: 'APPLIED', count: 6, share: 0.25 },
    { status: 'INTERVIEW', count: 5, share: 0.2083 },
    { status: 'OFFER', count: 2, share: 0.0833 },
    { status: 'REJECTED', count: 9, share: 0.375 },
    { status: 'SAVED', count: 2, share: 0.0833 },
  ],
  funnel: [
    { stage: 'APPLIED', label: 'Applied', count: 22, conversionFromPrevious: 1, conversionFromStart: 1 },
    { stage: 'ASSESSMENT', label: 'Assessment', count: 11, conversionFromPrevious: 0.5, conversionFromStart: 0.5 },
    { stage: 'INTERVIEW', label: 'Interview', count: 8, conversionFromPrevious: 0.7273, conversionFromStart: 0.3636 },
    { stage: 'OFFER', label: 'Offer', count: 2, conversionFromPrevious: 0.25, conversionFromStart: 0.0909 },
  ],
  topCompanies: [
    { company: 'Zoho', applications: 3, interviews: 2, offers: 1 },
    { company: 'Amazon', applications: 1, interviews: 1, offers: 0 },
  ],
};

const TRENDS = {
  months: 6,
  monthly: [
    { month: '2026-05', year: 2026, monthValue: 5, label: 'May 26', applications: 3, interviews: 1, responses: 2, offers: 0, rejections: 1 },
    { month: '2026-06', year: 2026, monthValue: 6, label: 'Jun 26', applications: 5, interviews: 3, responses: 4, offers: 1, rejections: 1 },
  ],
  totals: { applications: 8, interviews: 4, responses: 6, offers: 1, rejections: 2, bestMonth: 'Jun 26' },
};

const EVENTS = [
  {
    kind: 'INTERVIEW',
    id: 11,
    applicationId: 4,
    company: 'Zoho',
    jobTitle: 'Java Developer',
    title: 'Technical round',
    date: '2026-10-20',
    at: '2026-10-20T05:30:00Z',
    label: 'Mon, 20 Oct · 11:00',
    overdue: false,
    daysAway: 3,
  },
  {
    kind: 'DEADLINE',
    id: null,
    applicationId: 7,
    company: 'Atlassian',
    jobTitle: 'Backend Engineer',
    title: 'Application deadline',
    date: '2026-10-10',
    at: null,
    label: 'Deadline passed',
    overdue: true,
    daysAway: -7,
  },
];

const INTERVIEWS = [
  {
    id: 11,
    applicationId: 4,
    company: 'Zoho',
    jobTitle: 'Java Developer',
    roundType: 'TECHNICAL',
    scheduledAt: '2026-10-20T05:30:00Z',
    scheduledAtLocal: '2026-10-20T11:00:00+05:30',
    timezone: 'Asia/Kolkata',
    durationMinutes: 60,
    locationOrLink: 'https://meet.example.com/abc',
    interviewerName: 'Priya Nair',
    outcome: 'PENDING',
    upcoming: true,
  },
];

function renderDashboard() {
  localStorage.setItem('jobtrack.user', JSON.stringify({ id: 1, name: 'Asha Verma', role: 'USER', timezone: 'Asia/Kolkata' }));
  localStorage.setItem('jobtrack.accessToken', 'access.jwt.token');
  return render(
    <ToastProvider>
      <AuthProvider>
        <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
          <Dashboard />
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    analyticsApi.summary.mockResolvedValue(SUMMARY);
    analyticsApi.trends.mockResolvedValue(TRENDS);
    analyticsApi.upcoming.mockResolvedValue(EVENTS);
    interviewsApi.upcoming.mockResolvedValue(INTERVIEWS);
    userApi.me.mockResolvedValue({ id: 1, name: 'Asha Verma', role: 'USER', timezone: 'Asia/Kolkata' });
  });

  it('renders the rates exactly as the API computed them', async () => {
    renderDashboard();

    const kpis = await screen.findByRole('region', { name: /key metrics/i });
    expect(within(kpis).getByText('59.1%')).toBeInTheDocument();
    expect(within(kpis).getByText('36.4%')).toBeInTheDocument();
    expect(within(kpis).getByText('9.1%')).toBeInTheDocument();
    expect(within(kpis).getByText('24')).toBeInTheDocument();
    expect(within(kpis).getByText(/13 of 22 submitted got a reply/i)).toBeInTheDocument();
  });

  it('flags overdue work in the needs-attention KPI', async () => {
    renderDashboard();
    const kpis = await screen.findByRole('region', { name: /key metrics/i });
    // 1 overdue deadline + 3 next actions due
    expect(within(kpis).getByText('4')).toBeInTheDocument();
    expect(within(kpis).getByText(/1 overdue deadline · 3 next actions due/i)).toBeInTheDocument();
  });

  it('lists upcoming events and links each one to its application', async () => {
    renderDashboard();

    const section = (await screen.findByRole('heading', { name: 'Needs attention' })).closest('section');
    expect(within(section).getByText('Technical round')).toBeInTheDocument();
    expect(within(section).getByText('Application deadline')).toBeInTheDocument();

    const links = within(section).getAllByRole('link', { name: /open/i });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute('href', '/applications/4');
  });

  it('shows the scheduled rounds with the interviewer and a join link', async () => {
    renderDashboard();

    const section = (await screen.findByRole('heading', { name: 'Next interviews' })).closest('section');
    expect(within(section).getByText('Technical · Zoho')).toBeInTheDocument();
    expect(within(section).getByText(/Priya Nair/i)).toBeInTheDocument();
    expect(within(section).getByRole('link', { name: /join/i })).toHaveAttribute(
      'href',
      'https://meet.example.com/abc',
    );
  });

  it('renders the funnel stages from the audit trail, not from a client-side count', async () => {
    renderDashboard();

    const card = (await screen.findByText('Conversion funnel')).closest('section');
    const funnel = within(card).getByRole('list');
    expect(within(funnel).getByText('Applied')).toBeInTheDocument();
    expect(within(funnel).getByText('22')).toBeInTheDocument();
    expect(within(funnel).getByText('50% of previous')).toBeInTheDocument();

    // Every chart also ships a screen-reader data table with the same numbers.
    const table = card.querySelector('table.visually-hidden');
    expect(table).not.toBeNull();
    expect(within(table).getByText('Assessment')).toBeInTheDocument();
    expect(within(table).getByText('72.7%')).toBeInTheDocument();
  });

  it('only advertises top companies when a company actually repeats', async () => {
    renderDashboard();
    expect(await screen.findByText('Where you are applying most')).toBeInTheDocument();

    analyticsApi.summary.mockResolvedValue({
      ...SUMMARY,
      topCompanies: [{ company: 'Zoho', applications: 1, interviews: 0, offers: 0 }],
    });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /refresh/i }));

    await waitFor(() =>
      expect(screen.queryByText('Where you are applying most')).not.toBeInTheDocument(),
    );
  });

  it('invites a brand-new user to add their first application instead of showing empty charts', async () => {
    analyticsApi.summary.mockResolvedValue({ ...SUMMARY, totalApplications: 0, submittedApplications: 0 });
    renderDashboard();

    expect(await screen.findByText('No applications tracked yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /add your first application/i })).toHaveAttribute(
      'href',
      '/applications?new=1',
    );
    expect(screen.queryByRole('heading', { name: 'Needs attention' })).not.toBeInTheDocument();
  });

  it('refetches with the new window when the trend range changes', async () => {
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByRole('heading', { name: 'Needs attention' });
    expect(analyticsApi.trends).toHaveBeenCalledWith(6);

    await user.click(screen.getByRole('button', { name: '12M' }));

    await waitFor(() => expect(analyticsApi.trends).toHaveBeenCalledWith(12));
  });

  it('shows a retryable error state when analytics are unreachable', async () => {
    analyticsApi.summary.mockRejectedValue(Object.assign(new Error('Database is down'), { status: 500 }));
    const user = userEvent.setup();
    renderDashboard();

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Database is down')).toBeInTheDocument();

    analyticsApi.summary.mockResolvedValue(SUMMARY);
    await user.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByRole('heading', { name: 'Needs attention' })).toBeInTheDocument();
  });
});
