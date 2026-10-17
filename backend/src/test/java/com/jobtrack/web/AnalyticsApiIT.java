package com.jobtrack.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analytics integration tests: the numbers on the dashboard have to be *right*, and the rules have to
 * be consistent between the KPI cards, the funnel and the trend chart.
 *
 * <p>The dataset below is built through the public API (create as a draft, then walk the status), so
 * the audit trail the funnel reads is the same one a real user would produce.</p>
 *
 * <pre>
 *   A            SAVED                                        (draft - excluded everywhere)
 *   B..E (4)     SAVED -> APPLIED                             (submitted, no response)
 *   F            SAVED -> APPLIED -> ASSESSMENT               (responded)
 *   G            SAVED -> APPLIED -> ASSESSMENT -> INTERVIEW  (responded, interviewed)
 *   H            SAVED -> APPLIED -> INTERVIEW                (responded, interviewed, no assessment)
 *   I            SAVED -> APPLIED -> ASSESSMENT -> INTERVIEW -> OFFER
 *   J            SAVED -> APPLIED -> REJECTED                 (responded)
 * </pre>
 *
 * Expected: 10 total, 9 submitted, 5 responded, 3 reached interview, 1 offer, 1 rejection.
 */
class AnalyticsApiIT extends IntegrationTestSupport {

    private String token;

    @BeforeEach
    void buildDataset() throws Exception {
        token = registerAndReturnToken();

        walk("Draft Co", List.of());
        walk("Applied One", List.of("APPLIED"));
        walk("Applied Two", List.of("APPLIED"));
        walk("Applied Three", List.of("APPLIED"));
        walk("Applied Four", List.of("APPLIED"));
        walk("Assessed Co", List.of("APPLIED", "ASSESSMENT"));
        walk("Interviewed Co", List.of("APPLIED", "ASSESSMENT", "INTERVIEW"));
        walk("Fast Track Co", List.of("APPLIED", "INTERVIEW"));
        walk("Offered Co", List.of("APPLIED", "ASSESSMENT", "INTERVIEW", "OFFER"));
        walk("Rejected Co", List.of("APPLIED", "REJECTED"));
    }

    private void walk(String company, List<String> statuses) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("company", company);
        body.put("jobTitle", "Engineer");
        long id = readJson(mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("id").asLong();

        for (String status : statuses) {
            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", status)), token))
                    .andExpect(status().isOk());
        }
    }

    private JsonNode summary() throws Exception {
        return readJson(mockMvc.perform(asUser(get("/api/analytics/summary"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------- summary

    @Test
    @DisplayName("counts applications and keeps drafts out of every denominator")
    void countsApplicationsAndDrafts() throws Exception {
        JsonNode body = summary();

        assertThat(body.path("totalApplications").asLong()).isEqualTo(10);
        assertThat(body.path("submittedApplications").asLong()).isEqualTo(9);
        assertThat(body.path("savedDrafts").asLong()).isEqualTo(1);
        assertThat(body.path("activeApplications").asLong()).isEqualTo(7);   // 4 applied + 1 assessment + 2 interview
        assertThat(body.path("closedApplications").asLong()).isEqualTo(2);   // 1 offer + 1 rejected
        assertThat(body.path("respondedApplications").asLong()).isEqualTo(5);
        assertThat(body.path("reachedInterview").asLong()).isEqualTo(3);
        assertThat(body.path("offersReceived").asLong()).isEqualTo(1);
        assertThat(body.path("rejections").asLong()).isEqualTo(1);
        assertThat(body.path("withdrawals").asLong()).isZero();
    }

    @Test
    @DisplayName("all three rates share the same denominator: submitted applications")
    void ratesUseOneConsistentDenominator() throws Exception {
        JsonNode body = summary();

        // 5/9, 3/9, 1/9 - not 10 (which would silently reward keeping drafts), and not the
        // per-stage counts (which would double count).
        assertThat(body.path("responseRate").doubleValue()).isCloseTo(5.0 / 9.0, within(0.0001));
        assertThat(body.path("interviewRate").doubleValue()).isCloseTo(3.0 / 9.0, within(0.0001));
        assertThat(body.path("offerRate").doubleValue()).isCloseTo(1.0 / 9.0, within(0.0001));
    }

    @Test
    @DisplayName("an application with both an INTERVIEW status and interview rows counts once")
    void interviewCountIsNotDoubled() throws Exception {
        // Find the application that reached INTERVIEW through the assessment route and give it two
        // rounds: the "reached interview" counter must not move.
        JsonNode page = readJson(mockMvc.perform(asUser(get("/api/applications")
                        .param("status", "INTERVIEW").param("size", "10"), token))
                .andReturn().getResponse().getContentAsString());
        long applicationId = page.path("content").get(0).path("id").asLong();

        for (String round : List.of("PHONE_SCREEN", "TECHNICAL")) {
            Map<String, Object> interview = new HashMap<>();
            interview.put("roundType", round);
            interview.put("scheduledAt", java.time.Instant.now().plusSeconds(86400).toString());
            mockMvc.perform(asUser(json(post("/api/applications/" + applicationId + "/interviews"),
                            interview), token))
                    .andExpect(status().isCreated());
        }

        JsonNode body = summary();
        assertThat(body.path("reachedInterview").asLong()).isEqualTo(3);
        assertThat(body.path("interviewsScheduled").asLong()).isEqualTo(2);
        assertThat(body.path("upcomingInterviews").asLong()).isEqualTo(2);
        assertThat(body.path("interviewRate").doubleValue()).isCloseTo(3.0 / 9.0, within(0.0001));
    }

    @Test
    @DisplayName("status distribution lists every status, including the zeros")
    void distributionCoversEveryStatus() throws Exception {
        JsonNode distribution = summary().path("statusDistribution");

        assertThat(distribution).hasSize(7);
        Map<String, Long> counts = new HashMap<>();
        Map<String, Double> shares = new HashMap<>();
        distribution.forEach(slice -> {
            counts.put(slice.path("status").asText(), slice.path("count").asLong());
            shares.put(slice.path("status").asText(), slice.path("share").doubleValue());
        });

        assertThat(counts).containsEntry("SAVED", 1L)
                .containsEntry("APPLIED", 4L)
                .containsEntry("ASSESSMENT", 1L)
                .containsEntry("INTERVIEW", 2L)
                .containsEntry("OFFER", 1L)
                .containsEntry("REJECTED", 1L)
                .containsEntry("WITHDRAWN", 0L);

        // Shares are relative to *all* applications, so they add up to 1.
        assertThat(shares.values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, within(0.0001));
        assertThat(shares.get("APPLIED")).isCloseTo(4.0 / 10.0, within(0.0001));
    }

    @Test
    @DisplayName("the funnel is cumulative from the audit trail and never counts an application twice")
    void funnelIsCumulative() throws Exception {
        JsonNode funnel = summary().path("funnel");

        assertThat(funnel).hasSize(4);
        assertThat(funnel.get(0).path("stage").asText()).isEqualTo("APPLIED");
        assertThat(funnel.get(0).path("count").asLong()).isEqualTo(9);
        assertThat(funnel.get(0).path("conversionFromStart").doubleValue()).isCloseTo(1.0, within(0.0001));

        /*
         * The stages are cumulative: reaching a later stage implies the earlier ones, so an
         * application that jumped APPLIED -> INTERVIEW (H, no assessment row in the trail) still
         * counts at ASSESSMENT. That is what keeps the funnel monotonic when a candidate adopts the
         * tracker mid-process.
         */
        assertThat(funnel.get(1).path("stage").asText()).isEqualTo("ASSESSMENT");
        assertThat(funnel.get(1).path("count").asLong()).isEqualTo(4);   // F, G, H, I
        assertThat(funnel.get(1).path("conversionFromPrevious").doubleValue())
                .isCloseTo(4.0 / 9.0, within(0.0001));

        assertThat(funnel.get(2).path("stage").asText()).isEqualTo("INTERVIEW");
        assertThat(funnel.get(2).path("count").asLong()).isEqualTo(3);   // G, H, I
        assertThat(funnel.get(2).path("conversionFromPrevious").doubleValue())
                .isCloseTo(3.0 / 4.0, within(0.0001));
        assertThat(funnel.get(2).path("conversionFromStart").doubleValue())
                .isCloseTo(3.0 / 9.0, within(0.0001));

        assertThat(funnel.get(3).path("stage").asText()).isEqualTo("OFFER");
        assertThat(funnel.get(3).path("count").asLong()).isEqualTo(1);
        assertThat(funnel.get(3).path("conversionFromPrevious").doubleValue())
                .isCloseTo(1.0 / 3.0, within(0.0001));
        assertThat(funnel.get(3).path("conversionFromStart").doubleValue())
                .isCloseTo(1.0 / 9.0, within(0.0001));
    }

    @Test
    @DisplayName("the funnel is monotonic: no stage can be larger than the one before it")
    void funnelNeverGrows() throws Exception {
        JsonNode funnel = summary().path("funnel");

        long previous = Long.MAX_VALUE;
        for (JsonNode stage : funnel) {
            assertThat(stage.path("count").asLong()).isLessThanOrEqualTo(previous);
            previous = stage.path("count").asLong();
        }
    }

    @Test
    @DisplayName("with no data at all the rates are zero, never NaN or an error")
    void emptyAccountProducesZeroRates() throws Exception {
        String emptyUser = registerSecondUser();

        JsonNode body = readJson(mockMvc.perform(asUser(get("/api/analytics/summary"), emptyUser))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.path("totalApplications").asLong()).isZero();
        assertThat(body.path("responseRate").doubleValue()).isZero();
        assertThat(body.path("interviewRate").doubleValue()).isZero();
        assertThat(body.path("offerRate").doubleValue()).isZero();
        // nullable fields are omitted rather than sent as null (Jackson NON_NULL)
        assertThat(body.has("averageDaysToFirstResponse")).isFalse();
        assertThat(body.toString()).doesNotContain("NaN");
        assertThat(body.path("funnel")).hasSize(4);
    }

    @Test
    @DisplayName("analytics require authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/trends")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/upcoming")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("one user's data never appears in another user's analytics")
    void analyticsAreScopedToTheCaller() throws Exception {
        String other = registerSecondUser();
        Map<String, Object> body = new HashMap<>();
        body.put("company", "Other User Corp");
        body.put("jobTitle", "Engineer");
        body.put("status", "OFFER");
        mockMvc.perform(asUser(json(post("/api/applications"), body), other))
                .andExpect(status().isCreated());

        JsonNode mine = summary();
        assertThat(mine.path("totalApplications").asLong()).isEqualTo(10);
        assertThat(mine.path("offersReceived").asLong()).isEqualTo(1);

        JsonNode theirs = readJson(mockMvc.perform(asUser(get("/api/analytics/summary"), other))
                .andReturn().getResponse().getContentAsString());
        assertThat(theirs.path("totalApplications").asLong()).isEqualTo(1);
        assertThat(theirs.path("offersReceived").asLong()).isEqualTo(1);
        assertThat(theirs.path("offerRate").doubleValue()).isCloseTo(1.0, within(0.0001));

        mockMvc.perform(asUser(get("/api/applications").param("search", "Other User"), token))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // -------------------------------------------------------------------- trends

    @Test
    @DisplayName("the trend window is gap-filled and counts only submitted applications")
    void trendsAreGapFilled() throws Exception {
        JsonNode body = readJson(mockMvc.perform(asUser(get("/api/analytics/trends")
                        .param("months", "3"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.path("months").asInt()).isEqualTo(3);
        JsonNode monthly = body.path("monthly");
        assertThat(monthly).hasSize(3);

        YearMonth current = YearMonth.now();
        assertThat(monthly.get(2).path("month").asText())
                .isEqualTo("%d-%02d".formatted(current.getYear(), current.getMonthValue()));
        // The nine submitted applications all landed in the current month; the draft is excluded.
        assertThat(monthly.get(2).path("applications").asLong()).isEqualTo(9);
        assertThat(monthly.get(1).path("applications").asLong()).isZero();
        assertThat(monthly.get(0).path("applications").asLong()).isZero();
        assertThat(monthly.get(2).path("offers").asLong()).isEqualTo(1);
        assertThat(monthly.get(2).path("rejections").asLong()).isEqualTo(1);

        assertThat(body.path("totals").path("applications").asLong()).isEqualTo(9);
    }

    @Test
    @DisplayName("the trend window is bounded at 1..36 months, in the API and again in the service")
    void trendWindowIsBounded() throws Exception {
        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "1"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(1))
                .andExpect(jsonPath("$.monthly.length()").value(1));

        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "36"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(36))
                .andExpect(jsonPath("$.monthly.length()").value(36));

        // Out-of-range windows are rejected rather than producing an unbounded GROUP BY.
        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "0"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "1000"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "-5"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a non-numeric window is a 400")
    void rejectsNonNumericWindow() throws Exception {
        mockMvc.perform(asUser(get("/api/analytics/trends").param("months", "lots"), token))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------- upcoming events

    @Test
    @DisplayName("a future deadline shows up in the feed with a day count and no overdue flag")
    void upcomingEventsIncludeDeadlines() throws Exception {
        createWithDates("Deadline Co", LocalDate.now().plusDays(2), null);

        JsonNode events = upcoming(7, 25);

        List<String> kinds = kindsOf(events);
        assertThat(kinds).contains("DEADLINE");
        // Nothing is past due yet, so no reminder joins the feed.
        assertThat(kinds).doesNotContain("REMINDER", "NEXT_ACTION");

        JsonNode deadline = findFirst(events, "kind", "DEADLINE");
        assertThat(deadline.path("company").asText()).isEqualTo("Deadline Co");
        assertThat(deadline.path("title").asText()).isEqualTo("Application deadline");
        assertThat(deadline.path("applicationId").asLong()).isPositive();
        assertThat(deadline.path("daysAway").asLong()).isBetween(1L, 3L);
        assertThat(deadline.path("overdue").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a next action joins the feed once it is due, together with its auto reminder")
    void upcomingEventsIncludeDueNextActions() throws Exception {
        createWithDates("Followup Co", null, LocalDate.now());

        JsonNode events = upcoming(7, 25);
        List<String> kinds = kindsOf(events);

        assertThat(kinds).contains("NEXT_ACTION", "REMINDER");
        JsonNode nextAction = findFirst(events, "kind", "NEXT_ACTION");
        assertThat(nextAction.path("company").asText()).isEqualTo("Followup Co");
        assertThat(nextAction.path("title").asText()).isEqualTo("Send a follow-up");
    }

    @Test
    @DisplayName("a scheduled interview is the first thing a candidate needs to see")
    void upcomingEventsIncludeInterviews() throws Exception {
        long id = createWithDates("Interview Co", null, null);
        Map<String, Object> interview = new HashMap<>();
        interview.put("roundType", "TECHNICAL");
        interview.put("scheduledAt", java.time.Instant.now().plusSeconds(3600).toString());
        interview.put("durationMinutes", 60);
        mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"), interview), token))
                .andExpect(status().isCreated());

        JsonNode events = upcoming(7, 25);

        assertThat(events.get(0).path("kind").asText()).isEqualTo("INTERVIEW");
        assertThat(events.get(0).path("title").asText()).isEqualTo("technical interview");
        assertThat(events.get(0).path("applicationId").asLong()).isEqualTo(id);
        assertThat(events.get(0).path("id").asLong()).isPositive();
    }

    private long createWithDates(String company, LocalDate deadline, LocalDate nextActionDate)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("company", company);
        body.put("jobTitle", "Engineer");
        body.put("status", "APPLIED");
        body.put("appliedAt", LocalDate.now().toString());
        if (deadline != null) {
            body.put("deadline", deadline.toString());
        }
        if (nextActionDate != null) {
            body.put("nextAction", "Send a follow-up");
            body.put("nextActionDate", nextActionDate.toString());
        }
        return readJson(mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    private JsonNode upcoming(int days, int limit) throws Exception {
        return readJson(mockMvc.perform(asUser(get("/api/analytics/upcoming")
                        .param("days", String.valueOf(days)).param("limit", String.valueOf(limit)), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> kindsOf(JsonNode events) {
        assertThat(events.isArray()).isTrue();
        List<String> kinds = new java.util.ArrayList<>();
        events.forEach(event -> kinds.add(event.path("kind").asText()));
        return kinds;
    }

    @Test
    @DisplayName("an overdue deadline is reported as overdue and sorted first")
    void overdueEventsComeFirst() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("company", "Overdue Co");
        body.put("jobTitle", "Engineer");
        body.put("status", "APPLIED");
        body.put("appliedAt", LocalDate.now().minusDays(10).toString());
        body.put("deadline", LocalDate.now().minusDays(2).toString());
        mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                .andExpect(status().isCreated());

        // Also give the caller something that is NOT overdue, to prove the ordering.
        createWithDates("Future Co", LocalDate.now().plusDays(5), null);

        JsonNode events = upcoming(30, 25);

        JsonNode first = events.get(0);
        assertThat(first.path("overdue").asBoolean()).isTrue();
        assertThat(first.path("company").asText()).isEqualTo("Overdue Co");
        assertThat(kindsOf(events)).contains("DEADLINE", "REMINDER");

        // The non-overdue deadline is sorted after every overdue item.
        JsonNode future = findFirst(events, "company", "Future Co");
        assertThat(future.path("overdue").asBoolean()).isFalse();
        assertThat(events.toString().indexOf("Overdue Co"))
                .isLessThan(events.toString().indexOf("Future Co"));
    }

    private JsonNode findFirst(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }
}
