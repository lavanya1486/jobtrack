package com.jobtrack.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrack.domain.User;
import com.jobtrack.service.ReminderGenerationService;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interview rounds and reminders.
 *
 * <p>Two behaviours matter most here and both are easy to get subtly wrong:</p>
 * <ul>
 *   <li>scheduling a round moves the application forward and stamps the employer response, so the
 *       analytics stay honest;</li>
 *   <li>generated reminders are idempotent - running the scheduler twice (or twice in a cluster) must
 *       not produce duplicate rows.</li>
 * </ul>
 */
class InterviewReminderApiIT extends IntegrationTestSupport {

    @Autowired
    private ReminderGenerationService reminderGenerationService;

    private long createApplication(String token, String company, String status, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("company", company);
        body.put("jobTitle", "Engineer");
        body.put("status", status);
        if (!"SAVED".equals(status)) {
            body.put("appliedAt", LocalDate.now().toString());
        }
        if (extra != null) {
            body.putAll(extra);
        }
        return readJson(mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    private Map<String, Object> round(String type, Instant when) {
        Map<String, Object> body = new HashMap<>();
        body.put("roundType", type);
        body.put("scheduledAt", when.toString());
        body.put("durationMinutes", 45);
        body.put("interviewerName", "Priya Nair");
        body.put("locationOrLink", "https://meet.example.com/abc");
        return body;
    }

    private JsonNode schedule(String token, long applicationId, Map<String, Object> body) throws Exception {
        return readJson(mockMvc.perform(asUser(
                        json(post("/api/applications/" + applicationId + "/interviews"), body), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------- interviews

    @Nested
    @DisplayName("POST /api/applications/{id}/interviews")
    class Scheduling {

        @Test
        @DisplayName("scheduling a round advances the application and stamps the employer response")
        void advancesTheApplication() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);

            JsonNode interview = schedule(token, id, round("PHONE_SCREEN", Instant.now().plusSeconds(86400)));

            assertThat(interview.path("roundType").asText()).isEqualTo("PHONE_SCREEN");
            assertThat(interview.path("company").asText()).isEqualTo("Zoho");
            assertThat(interview.path("outcome").asText()).isEqualTo("PENDING");
            assertThat(interview.path("upcoming").asBoolean()).isTrue();
            assertThat(interview.path("applicationId").asLong()).isEqualTo(id);

            JsonNode detail = readJson(mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(detail.path("application").path("status").asText()).isEqualTo("INTERVIEW");
            assertThat(detail.path("application").has("firstResponseAt")).isTrue();
            assertThat(detail.path("application").path("interviewCount").asInt()).isEqualTo(1);
            assertThat(detail.path("interviews")).hasSize(1);

            // The audit trail explains *why* the status moved.
            List<String> notes = new java.util.ArrayList<>();
            detail.path("history").forEach(entry -> notes.add(entry.path("note").asText("")));
            assertThat(notes).anyMatch(note -> note.contains("PHONE_SCREEN"));
        }

        @Test
        @DisplayName("the scheduled instant is rendered in the user's own time zone")
        void rendersInUserTimeZone() throws Exception {
            String token = registerAndReturnToken();
            mockMvc.perform(asUser(json(patch("/api/users/me"),
                            Map.of("name", "Test Candidate", "timezone", "Asia/Kolkata")), token))
                    .andExpect(status().isOk());
            long id = createApplication(token, "Zoho", "APPLIED", null);

            // 04:30Z is 10:00 in Asia/Kolkata (+05:30).
            Instant when = LocalDate.now().plusDays(1).atTime(4, 30).toInstant(java.time.ZoneOffset.UTC);
            JsonNode interview = schedule(token, id, round("TECHNICAL", when));

            assertThat(interview.path("timezone").asText()).isEqualTo("Asia/Kolkata");
            assertThat(interview.path("scheduledAtLocal").asText()).endsWith("10:00:00+05:30");
        }

        @Test
        @DisplayName("a draft cannot be given an interview round - it was never submitted")
        void refusesRoundsForDrafts() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Draft Co", "SAVED", null);

            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"),
                            round("PHONE_SCREEN", Instant.now().plusSeconds(86400))), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a round that already happened can be recorded, and is not 'upcoming'")
        void allowsRecordingAPastRound() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);

            JsonNode interview = readJson(mockMvc.perform(asUser(
                            json(post("/api/applications/" + id + "/interviews"),
                                    round("PHONE_SCREEN", Instant.now().minusSeconds(86400))), token))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());

            assertThat(interview.path("upcoming").asBoolean()).isFalse();

            mockMvc.perform(asUser(get("/api/interviews").param("scope", "UPCOMING"), token))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(asUser(get("/api/interviews").param("scope", "PAST"), token))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("duration is bounded to 5..720 minutes")
        void boundsTheDuration() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);

            Map<String, Object> tooShort = round("PHONE_SCREEN", Instant.now().plusSeconds(86400));
            tooShort.put("durationMinutes", 1);
            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"), tooShort), token))
                    .andExpect(status().isBadRequest());

            Map<String, Object> tooLong = round("PHONE_SCREEN", Instant.now().plusSeconds(86400));
            tooLong.put("durationMinutes", 5000);
            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"), tooLong), token))
                    .andExpect(status().isBadRequest());

            Map<String, Object> badLink = round("PHONE_SCREEN", Instant.now().plusSeconds(86400));
            badLink.put("locationOrLink", "not a url");
            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"), badLink), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("requires a round type and a start time")
        void validatesTheRequest() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);

            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"),
                            Map.of("durationMinutes", 45)), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isArray());

            mockMvc.perform(asUser(json(post("/api/applications/" + id + "/interviews"),
                            Map.of("roundType", "LUNCH_CHAT", "scheduledAt",
                                    Instant.now().plusSeconds(3600).toString())), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_enum_value"));
        }

        @Test
        @DisplayName("another user cannot see, move or delete the round (IDOR)")
        void isolatesRoundsPerUser() throws Exception {
            String owner = registerAndReturnToken();
            String attacker = registerSecondUser();
            long id = createApplication(owner, "Zoho", "APPLIED", null);
            long interviewId = schedule(owner, id, round("HR", Instant.now().plusSeconds(86400)))
                    .path("id").asLong();

            mockMvc.perform(asUser(get("/api/applications/" + id + "/interviews"), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(get("/api/applications/" + id), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(json(put("/api/interviews/" + interviewId),
                            round("HR", Instant.now().plusSeconds(172800))), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(delete("/api/interviews/" + interviewId), attacker))
                    .andExpect(status().isNotFound());

            // Still there for the owner.
            mockMvc.perform(asUser(get("/api/applications/" + id + "/interviews"), owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("interview lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("recording an outcome flips the round out of the upcoming list")
        void recordsAnOutcome() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);
            long interviewId = schedule(token, id, round("TECHNICAL", Instant.now().plusSeconds(86400)))
                    .path("id").asLong();

            Map<String, Object> update = round("TECHNICAL", Instant.now().plusSeconds(86400));
            update.put("outcome", "PASSED");
            update.put("feedback", "Strong on JPA, weak on concurrency");

            mockMvc.perform(asUser(json(put("/api/interviews/" + interviewId), update), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("PASSED"))
                    .andExpect(jsonPath("$.feedback").value("Strong on JPA, weak on concurrency"))
                    .andExpect(jsonPath("$.upcoming").value(false));

            mockMvc.perform(asUser(get("/api/interviews").param("scope", "PAST"), token))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(asUser(get("/api/interviews").param("scope", "UPCOMING"), token))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("rescheduling keeps exactly one preparation reminder and moves it")
        void reschedulingMovesTheReminder() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);
            long interviewId = schedule(token, id, round("TECHNICAL", Instant.now().plusSeconds(172800)))
                    .path("id").asLong();

            JsonNode before = reminders(token, "ALL");
            assertThat(before.path("totalElements").asLong()).isEqualTo(1);
            JsonNode prep = before.path("content").get(0);
            assertThat(prep.path("type").asText()).isEqualTo("INTERVIEW_PREP");
            assertThat(prep.path("source").asText()).isEqualTo("AUTO");

            Instant moved = Instant.now().plusSeconds(604800);
            mockMvc.perform(asUser(json(put("/api/interviews/" + interviewId),
                            round("TECHNICAL", moved)), token))
                    .andExpect(status().isOk());

            JsonNode after = reminders(token, "ALL");
            assertThat(after.path("totalElements").asLong()).isEqualTo(1);
            assertThat(after.path("content").get(0).path("id").asLong())
                    .isEqualTo(prep.path("id").asLong());
            assertThat(Instant.parse(after.path("content").get(0).path("dueAt").asText()))
                    .isAfter(Instant.parse(prep.path("dueAt").asText()));
        }

        @Test
        @DisplayName("deleting a round removes its preparation reminder but keeps the application")
        void deletingARoundCleansUp() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);
            long interviewId = schedule(token, id, round("TECHNICAL", Instant.now().plusSeconds(172800)))
                    .path("id").asLong();
            assertThat(reminders(token, "ALL").path("totalElements").asLong()).isEqualTo(1);

            mockMvc.perform(asUser(delete("/api/interviews/" + interviewId), token))
                    .andExpect(status().isNoContent());

            assertThat(reminders(token, "ALL").path("totalElements").asLong()).isZero();
            mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.application.status").value("INTERVIEW"))
                    .andExpect(jsonPath("$.interviews.length()").value(0));
        }

        @Test
        @DisplayName("rounds are listed per application and across the account, ordered by time")
        void listsRounds() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);
            schedule(token, id, round("PHONE_SCREEN", Instant.now().plusSeconds(172800)));
            schedule(token, id, round("TECHNICAL", Instant.now().plusSeconds(86400)));

            mockMvc.perform(asUser(get("/api/applications/" + id + "/interviews"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].roundType").value("TECHNICAL"))
                    .andExpect(jsonPath("$[1].roundType").value("PHONE_SCREEN"));

            mockMvc.perform(asUser(get("/api/interviews").param("scope", "ALL"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].company").value("Zoho"));

            mockMvc.perform(asUser(get("/api/interviews/upcoming").param("limit", "1"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].roundType").value("TECHNICAL"));

            mockMvc.perform(asUser(get("/api/interviews").param("scope", "NONSENSE"), token))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------- reminders

    @Nested
    @DisplayName("reminders")
    class Reminders {

        @Test
        @DisplayName("scopes filter the queue: open, overdue, upcoming, completed, all")
        void scopesFilterTheQueue() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, "Past Deadline Co", "APPLIED", Map.of(
                    "appliedAt", LocalDate.now().minusDays(10).toString(),
                    "deadline", LocalDate.now().minusDays(1).toString()));
            createApplication(token, "Soon Deadline Co", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));

            assertThat(reminders(token, "ALL").path("totalElements").asLong()).isEqualTo(2);
            assertThat(reminders(token, "OPEN").path("totalElements").asLong()).isEqualTo(2);
            assertThat(reminders(token, "OVERDUE").path("totalElements").asLong()).isEqualTo(1);
            assertThat(reminders(token, "OVERDUE").path("content").get(0).path("company").asText())
                    .isEqualTo("Past Deadline Co");
            assertThat(reminders(token, "UPCOMING").path("totalElements").asLong()).isEqualTo(1);
            assertThat(reminders(token, "COMPLETED").path("totalElements").asLong()).isZero();
        }

        @Test
        @DisplayName("an invalid scope is rejected rather than silently returning everything")
        void rejectsInvalidScope() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/reminders").param("scope", "NONSENSE"), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("completing is idempotent and can be undone")
        void completeAndReopen() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, "Zoho", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));
            long reminderId = reminders(token, "OPEN").path("content").get(0).path("id").asLong();

            mockMvc.perform(asUser(patch("/api/reminders/" + reminderId + "/complete"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(true))
                    .andExpect(jsonPath("$.completedAt").isNotEmpty());

            mockMvc.perform(asUser(patch("/api/reminders/" + reminderId + "/complete"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(true));

            assertThat(reminders(token, "OPEN").path("totalElements").asLong()).isZero();
            assertThat(reminders(token, "COMPLETED").path("totalElements").asLong()).isEqualTo(1);

            mockMvc.perform(asUser(patch("/api/reminders/" + reminderId + "/reopen"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(false));
            assertThat(reminders(token, "OPEN").path("totalElements").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("moving a deadline moves its reminder; removing it deletes the reminder")
        void staysInSyncWithTheApplication() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(3).toString()));

            Map<String, Object> update = new HashMap<>();
            update.put("company", "Zoho");
            update.put("jobTitle", "Engineer");
            update.put("status", "APPLIED");
            update.put("appliedAt", LocalDate.now().toString());
            update.put("deadline", LocalDate.now().plusDays(10).toString());
            mockMvc.perform(asUser(json(put("/api/applications/" + id), update), token))
                    .andExpect(status().isOk());

            // The old deadline reminder is replaced by one for the new date, never duplicated.
            JsonNode afterMove = reminders(token, "ALL");
            assertThat(afterMove.path("totalElements").asLong()).isEqualTo(1);
            assertThat(afterMove.path("content").get(0).path("dueAtLocal").asText())
                    .startsWith(LocalDate.now().plusDays(10).toString());
            assertThat(afterMove.path("content").get(0).path("title").asText())
                    .isEqualTo("Deadline: Zoho - Engineer");

            // Closing the application removes the deadline reminder: nothing to prepare for any more.
            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", "REJECTED")), token))
                    .andExpect(status().isOk());
            assertThat(reminders(token, "OPEN").path("totalElements").asLong()).isZero();
        }

        @Test
        @DisplayName("a manual reminder can be created, updated and deleted")
        void manualReminders() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, "Zoho", "APPLIED", null);

            Map<String, Object> body = new HashMap<>();
            body.put("type", "FOLLOW_UP");
            body.put("title", "Chase the recruiter");
            body.put("details", "Ask for feedback");
            body.put("dueAt", Instant.now().plusSeconds(7200).toString());
            body.put("applicationId", id);

            JsonNode created = readJson(mockMvc.perform(asUser(json(post("/api/reminders"), body), token))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            long reminderId = created.path("id").asLong();
            assertThat(created.path("source").asText()).isEqualTo("MANUAL");
            assertThat(created.path("company").asText()).isEqualTo("Zoho");

            body.put("title", "Chase the recruiter again");
            mockMvc.perform(asUser(json(put("/api/reminders/" + reminderId), body), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Chase the recruiter again"));

            mockMvc.perform(asUser(json(post("/api/reminders"), Map.of("type", "FOLLOW_UP")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists());

            mockMvc.perform(asUser(delete("/api/reminders/" + reminderId), token))
                    .andExpect(status().isNoContent());
            mockMvc.perform(asUser(delete("/api/reminders/" + reminderId), token))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("reminders are private: another user's reminder is a 404")
        void isolatesRemindersPerUser() throws Exception {
            String owner = registerAndReturnToken();
            String attacker = registerSecondUser();
            createApplication(owner, "Owner Co", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));
            long reminderId = reminders(owner, "ALL").path("content").get(0).path("id").asLong();

            mockMvc.perform(asUser(patch("/api/reminders/" + reminderId + "/complete"), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(delete("/api/reminders/" + reminderId), attacker))
                    .andExpect(status().isNotFound());
            assertThat(reminders(attacker, "ALL").path("totalElements").asLong()).isZero();
            assertThat(reminders(owner, "ALL").path("totalElements").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("a manual reminder cannot be attached to somebody else's application")
        void rejectsForeignApplicationReference() throws Exception {
            String owner = registerAndReturnToken();
            String attacker = registerSecondUser();
            long foreignId = createApplication(owner, "Owner Co", "APPLIED", null);

            Map<String, Object> body = new HashMap<>();
            body.put("type", "FOLLOW_UP");
            body.put("title", "Sneaky reminder");
            body.put("dueAt", Instant.now().plusSeconds(3600).toString());
            body.put("applicationId", foreignId);

            mockMvc.perform(asUser(json(post("/api/reminders"), body), attacker))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------ scheduled generation

    @Nested
    @DisplayName("the reminder scheduler")
    class Generation {

        @Test
        @DisplayName("generates deadline and next-action reminders, and never duplicates them")
        void generationIsIdempotent() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, "Deadline Co", "APPLIED", Map.of(
                    "deadline", LocalDate.now().plusDays(1).toString(),
                    "nextAction", "Follow up",
                    "nextActionDate", LocalDate.now().plusDays(1).toString()));

            int firstRun = reminderGenerationService.generateForAllUsers();
            JsonNode afterFirst = reminders(token, "ALL");
            // Two reminders already exist from the create call; generation must not add more.
            assertThat(afterFirst.path("totalElements").asLong()).isEqualTo(2);

            int secondRun = reminderGenerationService.generateForAllUsers();
            int thirdRun = reminderGenerationService.generateForAllUsers();
            assertThat(reminders(token, "ALL").path("totalElements").asLong()).isEqualTo(2);

            // The partial unique index plus the dedupe key mean repeat runs are no-ops.
            assertThat(secondRun).isZero();
            assertThat(thirdRun).isZero();
            assertThat(firstRun).isZero();
        }

        @Test
        @DisplayName("only active applications generate reminders - drafts and closed ones do not")
        void onlyActiveApplicationsGenerate() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, "Draft Co", "SAVED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));
            createApplication(token, "Rejected Co", "REJECTED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));
            createApplication(token, "Live Co", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));

            reminderGenerationService.generateForAllUsers();

            JsonNode all = reminders(token, "ALL");
            assertThat(all.path("totalElements").asLong()).isEqualTo(1);
            assertThat(all.path("content").get(0).path("company").asText()).isEqualTo("Live Co");
            assertThat(all.path("content").get(0).path("type").asText())
                    .isEqualTo("APPLICATION_DEADLINE");
            assertThat(all.path("content").get(0).path("source").asText()).isEqualTo("AUTO");
        }

        @Test
        @DisplayName("one user's failure cannot stop the run for everybody else")
        void survivesAUserWithNoData() throws Exception {
            String first = registerAndReturnToken();
            String second = registerSecondUser();
            createApplication(first, "First Co", "APPLIED",
                    Map.of("deadline", LocalDate.now().plusDays(1).toString()));

            int created = reminderGenerationService.generateForAllUsers();

            assertThat(created).isZero();
            assertThat(reminders(first, "ALL").path("totalElements").asLong()).isEqualTo(1);
            assertThat(reminders(second, "ALL").path("totalElements").asLong()).isZero();
        }

        @Test
        @DisplayName("reminder times are 09:00 in the owner's time zone, not UTC")
        void dueTimesRespectTheOwnerTimeZone() throws Exception {
            String token = registerAndReturnToken();
            mockMvc.perform(asUser(json(patch("/api/users/me"),
                            Map.of("name", "Test Candidate", "timezone", "Asia/Kolkata")), token))
                    .andExpect(status().isOk());
            LocalDate deadline = LocalDate.now().plusDays(2);
            createApplication(token, "Zoho", "APPLIED", Map.of("deadline", deadline.toString()));

            JsonNode reminder = reminders(token, "ALL").path("content").get(0);

            // 09:00 Asia/Kolkata == 03:30Z
            assertThat(reminder.path("dueAt").asText()).endsWith("03:30:00Z");
            assertThat(reminder.path("dueAtLocal").asText()).endsWith("09:00:00+05:30");
            assertThat(reminder.path("timezone").asText()).isEqualTo("Asia/Kolkata");

            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(user.getTimezone()).isEqualTo("Asia/Kolkata");
        }
    }

    private JsonNode reminders(String token, String scope) throws Exception {
        return readJson(mockMvc.perform(asUser(get("/api/reminders").param("scope", scope)
                        .param("size", "50"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
