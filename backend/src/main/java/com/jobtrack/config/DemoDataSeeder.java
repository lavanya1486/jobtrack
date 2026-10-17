package com.jobtrack.config;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.ApplicationStatusHistory;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.ReminderSource;
import com.jobtrack.domain.ReminderType;
import com.jobtrack.domain.Role;
import com.jobtrack.domain.RoundType;
import com.jobtrack.domain.User;
import com.jobtrack.domain.WorkMode;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import com.jobtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

/**
 * Seeds a demo account with a realistic job search so the dashboard shows real charts on a fresh
 * database (live demo, screenshots, reviewer convenience).
 *
 * <p>Off by default: it only runs when {@code app.seed.demo-data=true}. The data is generated from a
 * fixed random seed, so two deployments of the demo look identical - useful when comparing
 * screenshots or debugging analytics.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.seed", name = "demo-data", havingValue = "true")
public class DemoDataSeeder {

    private static final long RANDOM_SEED = 20260101L;
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ReminderRepository reminderRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Clock clock;

    @Bean
    public ApplicationRunner demoDataRunner() {
        return args -> seed();
    }

    @Transactional
    public void seed() {
        User admin = ensureUser(appProperties.seed().adminEmail(), "JobTrack Admin",
                appProperties.seed().adminPassword(), Role.ADMIN);
        User demo = ensureUser(appProperties.seed().demoEmail(), "Asha Verma",
                appProperties.seed().demoPassword(), Role.USER);

        if (applicationRepository.countByUserId(demo.getId()) > 0) {
            log.info("Demo data already present for {}; skipping seed", demo.getEmail());
            return;
        }

        Random random = new Random(RANDOM_SEED);
        LocalDate today = LocalDate.now(clock);

        record Sample(String company, String title, String location, WorkMode mode,
                      EmploymentType employment, ApplicationStatus status, int daysAgoApplied,
                      String source, int salaryMin, int salaryMax, String[] tags) {
        }

        List<Sample> samples = List.of(
                new Sample("Zoho", "Java Developer Intern", "Chennai, India", WorkMode.HYBRID,
                        EmploymentType.INTERNSHIP, ApplicationStatus.INTERVIEW, 42, "Company career page",
                        300000, 450000, new String[]{"referral", "spring-boot"}),
                new Sample("Infosys", "Systems Engineer", "Bengaluru, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.OFFER, 96, "LinkedIn",
                        450000, 600000, new String[]{"campus", "mass-recruiter"}),
                new Sample("ThoughtWorks", "Associate Consultant", "Pune, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.REJECTED, 74, "LinkedIn",
                        700000, 900000, new String[]{"pair-programming"}),
                new Sample("Freshworks", "Backend Engineer", "Chennai, India", WorkMode.REMOTE,
                        EmploymentType.FULL_TIME, ApplicationStatus.ASSESSMENT, 18, "Referral",
                        900000, 1200000, new String[]{"referral"}),
                new Sample("Razorpay", "Software Engineer Intern", "Bengaluru, India", WorkMode.HYBRID,
                        EmploymentType.INTERNSHIP, ApplicationStatus.APPLIED, 6, "LinkedIn",
                        600000, 800000, new String[]{"fintech"}),
                new Sample("Flipkart", "SDE 1", "Bengaluru, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.INTERVIEW, 31, "Company career page",
                        1800000, 2400000, new String[]{"dsa", "system-design"}),
                new Sample("Swiggy", "Backend Engineer", "Bengaluru, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.REJECTED, 58, "LinkedIn",
                        1400000, 1800000, new String[]{"dsa"}),
                new Sample("Atlassian", "Full Stack Engineer", "Pune, India", WorkMode.REMOTE,
                        EmploymentType.FULL_TIME, ApplicationStatus.APPLIED, 11, "Referral",
                        2000000, 2600000, new String[]{"referral", "react"}),
                new Sample("TCS", "Systems Engineer", "Indore, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.OFFER, 121, "Campus drive",
                        400000, 500000, new String[]{"campus"}),
                new Sample("Wipro", "Project Engineer", "Indore, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.WITHDRAWN, 88, "Naukri",
                        380000, 450000, new String[]{}),
                new Sample("HDFC Bank", "Java Developer", "Mumbai, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.ASSESSMENT, 23, "LinkedIn",
                        800000, 1000000, new String[]{"banking"}),
                new Sample("Zomato", "Backend Engineer", "Gurugram, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.APPLIED, 4, "LinkedIn",
                        1500000, 2000000, new String[]{"food-tech"}),
                new Sample("Paytm", "Software Engineer", "Noida, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.REJECTED, 65, "Naukri",
                        900000, 1200000, new String[]{"fintech"}),
                new Sample("Dream11", "Java Backend Engineer", "Mumbai, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.INTERVIEW, 27, "Referral",
                        1600000, 2100000, new String[]{"referral", "high-scale"}),
                new Sample("Cognizant", "Programmer Analyst", "Pune, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.SAVED, 2, "Company career page",
                        500000, 650000, new String[]{"draft"}),
                new Sample("Meesho", "SDE Intern", "Bengaluru, India", WorkMode.REMOTE,
                        EmploymentType.INTERNSHIP, ApplicationStatus.APPLIED, 9, "LinkedIn",
                        480000, 600000, new String[]{"internship"}),
                new Sample("PhonePe", "Backend Engineer", "Bengaluru, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.ASSESSMENT, 15, "Referral",
                        1700000, 2200000, new String[]{"referral", "fintech"}),
                new Sample("Persistent Systems", "Java Developer", "Pune, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.REJECTED, 49, "Naukri",
                        700000, 900000, new String[]{"service-based"}),
                new Sample("Oracle", "Associate Consultant", "Bengaluru, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.APPLIED, 3, "LinkedIn",
                        1200000, 1600000, new String[]{"product-based"}),
                new Sample("Amazon", "SDE Intern", "Hyderabad, India", WorkMode.ONSITE,
                        EmploymentType.INTERNSHIP, ApplicationStatus.INTERVIEW, 35, "Referral",
                        1100000, 1400000, new String[]{"referral", "dsa", "oa"}),
                new Sample("Cipla", "IT Trainee", "Indore, India", WorkMode.ONSITE,
                        EmploymentType.FULL_TIME, ApplicationStatus.SAVED, 1, "Company career page",
                        350000, 420000, new String[]{"draft"}),
                new Sample("Myntra", "Backend Engineer", "Bengaluru, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.WITHDRAWN, 53, "LinkedIn",
                        1500000, 1900000, new String[]{"e-commerce"}),
                new Sample("Deloitte", "Analyst - Engineering", "Hyderabad, India", WorkMode.HYBRID,
                        EmploymentType.FULL_TIME, ApplicationStatus.APPLIED, 13, "Campus drive",
                        800000, 1000000, new String[]{"consulting"}),
                new Sample("Postman", "API Engineer", "Bengaluru, India", WorkMode.REMOTE,
                        EmploymentType.FULL_TIME, ApplicationStatus.APPLIED, 7, "LinkedIn",
                        1800000, 2300000, new String[]{"api", "remote"})
        );

        int interviewsCreated = 0;
        for (Sample sample : samples) {
            LocalDate appliedAt = today.minusDays(sample.daysAgoApplied());
            JobApplication application = new JobApplication(demo.getId(), sample.company(), sample.title());
            application.setLocation(sample.location());
            application.setWorkMode(sample.mode());
            application.setEmploymentType(sample.employment());
            application.setSource(sample.source());
            application.setCurrency("INR");
            application.setExpectedSalaryMin(BigDecimal.valueOf(sample.salaryMin()));
            application.setExpectedSalaryMax(BigDecimal.valueOf(sample.salaryMax()));
            application.setJobUrl("https://careers.example.com/" + sample.company().toLowerCase()
                    .replaceAll("[^a-z0-9]", "-"));
            application.setJobDescription("Build and maintain production services with Java "
                    + sample.title() + " responsibilities at " + sample.company() + ".");
            application.setTags(new java.util.LinkedHashSet<>(List.of(sample.tags())));
            application.setAppliedAt(sample.status() == ApplicationStatus.SAVED ? null : appliedAt);
            application.setDeadline(sample.status().isActive() && random.nextInt(3) == 0
                    ? today.plusDays(3 + random.nextInt(20))
                    : null);
            application.setNextAction(sample.status().isActive() ? "Follow up with the recruiter" : null);
            application.setNextActionDate(sample.status().isActive() && random.nextInt(2) == 0
                    ? today.plusDays(random.nextInt(7) - 2)
                    : null);
            application.setNotes("Found via " + sample.source() + ".");

            // Walk the audit trail so the funnel reflects stages actually reached.
            List<ApplicationStatus> path = pathTo(sample.status(), random);
            application.setStatus(path.getLast());
            if (path.getLast().countsAsEmployerResponse()) {
                application.setFirstResponseAt(appliedAt.plusDays(3 + random.nextInt(12))
                        .atStartOfDay(DEMO_ZONE).toInstant());
            }
            applicationRepository.save(application);

            ApplicationStatus previous = null;
            int step = 0;
            for (ApplicationStatus status : path) {
                ApplicationStatusHistory entry = ApplicationStatusHistory.of(
                        application.getId(), previous, status,
                        previous == null ? "Application created" : "Moved to " + status,
                        demo.getId());
                entry.setChangedAt(appliedAt.plusDays(step * (4 + random.nextInt(6)))
                        .atStartOfDay(DEMO_ZONE).toInstant());
                statusHistoryRepository.save(entry);
                previous = status;
                step++;
            }

            // Interview rounds for anything that reached the interview stage.
            if (path.contains(ApplicationStatus.INTERVIEW) || sample.status() == ApplicationStatus.OFFER) {
                interviewsCreated += createRounds(application, appliedAt, sample.status(), random, today);
            }

            if (sample.status().isActive() && application.getDeadline() != null) {
                Reminder reminder = new Reminder(demo.getId(), application.getId(),
                        ReminderType.APPLICATION_DEADLINE, ReminderSource.AUTO,
                        "Deadline: " + sample.company() + " - " + sample.title(),
                        application.getDeadline().atStartOfDay(DEMO_ZONE).toInstant());
                reminder.setDedupeKey("deadline:" + application.getId() + ":" + application.getDeadline());
                reminder.setDetails(sample.company() + " - " + sample.title());
                reminderRepository.save(reminder);
            }
        }

        log.info("Seeded demo data: user={} admin={} applications={} interviews={}",
                demo.getEmail(), admin.getEmail(), samples.size(), interviewsCreated);
        log.info("Demo credentials: {} / {}  (admin: {} / {})",
                demo.getEmail(), appProperties.seed().demoPassword(),
                admin.getEmail(), appProperties.seed().adminPassword());
    }

    private int createRounds(JobApplication application, LocalDate appliedAt,
                             ApplicationStatus finalStatus, Random random, LocalDate today) {
        int created = 0;
        RoundType[] progression = {RoundType.RECRUITER_CALL, RoundType.CODING_ASSESSMENT,
                RoundType.TECHNICAL, RoundType.HR};
        int rounds = finalStatus == ApplicationStatus.OFFER ? 4 : 1 + random.nextInt(3);

        for (int i = 0; i < Math.min(rounds, progression.length); i++) {
            boolean future = finalStatus.isActive() && i == Math.min(rounds, progression.length) - 1
                    && random.nextInt(2) == 0;
            Instant scheduledAt = future
                    ? ZonedDateTime.of(today.plusDays(1 + random.nextInt(10)),
                            java.time.LocalTime.of(10 + random.nextInt(8), random.nextInt(2) == 0 ? 0 : 30),
                            DEMO_ZONE).toInstant()
                    : appliedAt.plusDays(7L + i * 5L + random.nextInt(4))
                            .atStartOfDay(DEMO_ZONE).toInstant();

            Interview interview = new Interview();
            interview.setApplication(application);
            interview.setRoundType(progression[i]);
            interview.setScheduledAt(scheduledAt);
            interview.setDurationMinutes(30 + random.nextInt(4) * 15);
            interview.setInterviewerName(List.of("Priya Nair", "Rahul Mehta", "Sneha Kulkarni",
                    "Arjun Rao").get(random.nextInt(4)));
            interview.setLocationOrLink("https://meet.example.com/" + application.getId() + "-" + i);
            interview.setPrepNotes("Revise " + progression[i].name().replace('_', ' ').toLowerCase()
                    + " topics and prepare project questions.");
            if (!future) {
                interview.setOutcome(finalStatus == ApplicationStatus.REJECTED && i == rounds - 1
                        ? InterviewOutcome.FAILED : InterviewOutcome.PASSED);
                interview.setFeedback(interview.getOutcome() == InterviewOutcome.PASSED
                        ? "Clear communication, strong fundamentals."
                        : "Needs more depth on system design.");
            }
            interviewRepository.save(interview);
            created++;
        }
        return created;
    }

    /** A believable progression ending in the requested status. */
    private List<ApplicationStatus> pathTo(ApplicationStatus target, Random random) {
        return switch (target) {
            case SAVED -> List.of(ApplicationStatus.SAVED);
            case APPLIED -> List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
            case ASSESSMENT -> List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                    ApplicationStatus.ASSESSMENT);
            case INTERVIEW -> List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                    ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW);
            case OFFER -> List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                    ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
            case REJECTED -> random.nextBoolean()
                    ? List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                            ApplicationStatus.REJECTED)
                    : List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                            ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW,
                            ApplicationStatus.REJECTED);
            case WITHDRAWN -> List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
                    ApplicationStatus.WITHDRAWN);
        };
    }

    private User ensureUser(String email, String name, String password, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = User.of(name, email, passwordEncoder.encode(password), role);
            user.setTimezone(DEMO_ZONE.getId());
            userRepository.save(user);
            log.info("Created {} account {}", role, email);
            return user;
        });
    }
}
