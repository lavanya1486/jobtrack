package com.jobtrack.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application CRUD, search, filtering, sorting and pagination - plus the per-user isolation rules
 * that make those features safe.
 */
class ApplicationApiIT extends IntegrationTestSupport {

    private Map<String, Object> newApplication(String company, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("company", company);
        body.put("jobTitle", title);
        return body;
    }

    private long createApplication(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(response).path("id").asLong();
    }

    // ------------------------------------------------------------------ create

    @Nested
    @DisplayName("POST /api/applications")
    class Create {

        @Test
        @DisplayName("creates a draft with server-side derived fields and 201 + Location")
        void createsAnApplication() throws Exception {
            String token = registerAndReturnToken();
            Map<String, Object> body = newApplication("Zoho", "Java Developer Intern");
            body.put("status", "SAVED");
            body.put("employmentType", "INTERNSHIP");
            body.put("workMode", "REMOTE");
            body.put("location", "Chennai");
            body.put("currency", "INR");
            body.put("expectedSalaryMin", 600000);
            body.put("expectedSalaryMax", 900000);
            body.put("tags", List.of("Referral", "SPRING-BOOT"));
            body.put("notes", "Applied through a friend");

            mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/applications/")))
                    .andExpect(jsonPath("$.company").value("Zoho"))
                    .andExpect(jsonPath("$.status").value("SAVED"))
                    .andExpect(jsonPath("$.employmentType").value("INTERNSHIP"))
                    .andExpect(jsonPath("$.workMode").value("REMOTE"))
                    .andExpect(jsonPath("$.expectedSalaryMin").value(600000))
                    // A draft has not been submitted, so it stays out of every rate denominator.
                    .andExpect(jsonPath("$.submitted").value(false))
                    .andExpect(jsonPath("$.firstResponseAt").doesNotExist())
                    // Tags are normalised to lower case so filtering is predictable.
                    .andExpect(jsonPath("$.tags[0]").value("referral"))
                    .andExpect(jsonPath("$.tags[1]").value("spring-boot"))
                    .andExpect(jsonPath("$.interviewCount").value(0))
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        @DisplayName("missing company or job title is a 400 with field errors")
        void validatesRequiredFields() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(json(post("/api/applications"), Map.of("jobTitle", "Engineer")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'company')]").exists());

            mockMvc.perform(asUser(json(post("/api/applications"), Map.of("company", "Zoho")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'jobTitle')]").exists());
        }

        @Test
        @DisplayName("an inverted salary range or a deadline before the application date is rejected")
        void validatesCrossFieldRules() throws Exception {
            String token = registerAndReturnToken();

            Map<String, Object> inverted = newApplication("Zoho", "Engineer");
            inverted.put("expectedSalaryMin", 900000);
            inverted.put("expectedSalaryMax", 600000);
            mockMvc.perform(asUser(json(post("/api/applications"), inverted), token))
                    .andExpect(status().isBadRequest());

            Map<String, Object> badDates = newApplication("Zoho", "Engineer");
            badDates.put("appliedAt", "2026-05-10");
            badDates.put("deadline", "2026-05-01");
            mockMvc.perform(asUser(json(post("/api/applications"), badDates), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an unknown enum value is a 400 that lists the accepted values")
        void rejectsUnknownEnum() throws Exception {
            String token = registerAndReturnToken();
            Map<String, Object> body = newApplication("Zoho", "Engineer");
            body.put("status", "HIRED");

            mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_enum_value"))
                    .andExpect(jsonPath("$.details.acceptedValues").isArray());
        }

        @Test
        @DisplayName("requires authentication")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(json(post("/api/applications"), newApplication("Zoho", "Engineer")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------------------- read

    @Nested
    @DisplayName("GET /api/applications and /api/applications/{id}")
    class Read {

        @Test
        @DisplayName("returns the application with its rounds and status timeline")
        void returnsDetail() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.application.id").value(id))
                    .andExpect(jsonPath("$.application.company").value("Zoho"))
                    .andExpect(jsonPath("$.interviews").isArray())
                    .andExpect(jsonPath("$.history").isArray())
                    .andExpect(jsonPath("$.history[0].toStatus").value("SAVED"));
        }

        @Test
        @DisplayName("another user's id answers 404, never 403 and never the data (IDOR)")
        void hidesOtherUsersRecords() throws Exception {
            String owner = registerAndReturnToken();
            String attacker = registerSecondUser();
            long id = createApplication(owner, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(get("/api/applications/" + id), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(put("/api/applications/" + id), attacker)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newApplication("Evil", "Hijack"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(delete("/api/applications/" + id), attacker))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(get("/api/applications/" + id + "/status-history"), attacker))
                    .andExpect(status().isNotFound());

            // The owner still sees it - the attacker's probes changed nothing.
            mockMvc.perform(asUser(get("/api/applications/" + id), owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.application.company").value("Zoho"));
        }

        @Test
        @DisplayName("an id that does not exist at all is also 404")
        void unknownIdIsNotFound() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/applications/99999999"), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // --------------------------------------------------------- search & filters

    @Nested
    @DisplayName("GET /api/applications filtering")
    class Filtering {

        @Test
        @DisplayName("searches company, title and location case-insensitively")
        void searchesText() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, newApplication("Zoho Corporation", "Java Developer"));
            createApplication(token, newApplication("Atlassian", "Backend Engineer"));
            Map<String, Object> bengaluru = newApplication("Freshworks", "SRE");
            bengaluru.put("location", "Bengaluru");
            createApplication(token, bengaluru);

            mockMvc.perform(asUser(get("/api/applications").param("search", "zOHO"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Zoho Corporation"));

            mockMvc.perform(asUser(get("/api/applications").param("search", "engineer"), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Atlassian"));

            mockMvc.perform(asUser(get("/api/applications").param("search", "bengaluru"), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Freshworks"));
        }

        @Test
        @DisplayName("LIKE wildcards in the search term are escaped, not interpreted")
        void escapesLikeWildcards() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, newApplication("Zoho", "Java Developer"));
            createApplication(token, newApplication("Atlassian", "Backend Engineer"));

            mockMvc.perform(asUser(get("/api/applications").param("search", "%"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            mockMvc.perform(asUser(get("/api/applications").param("search", "_"), token))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("combines status, employment type, work mode and tag filters with AND")
        void combinesFilters() throws Exception {
            String token = registerAndReturnToken();
            Map<String, Object> first = newApplication("Zoho", "Java Developer");
            first.put("status", "APPLIED");
            first.put("appliedAt", LocalDate.now().minusDays(3).toString());
            first.put("employmentType", "FULL_TIME");
            first.put("workMode", "REMOTE");
            first.put("tags", List.of("referral"));
            createApplication(token, first);

            Map<String, Object> second = newApplication("Atlassian", "Backend Engineer");
            second.put("status", "INTERVIEW");
            second.put("appliedAt", LocalDate.now().minusDays(30).toString());
            second.put("employmentType", "FULL_TIME");
            second.put("workMode", "HYBRID");
            createApplication(token, second);

            Map<String, Object> third = newApplication("Saved Draft Co", "Intern");
            third.put("status", "SAVED");
            third.put("employmentType", "INTERNSHIP");
            createApplication(token, third);

            mockMvc.perform(asUser(get("/api/applications")
                            .param("status", "APPLIED").param("status", "INTERVIEW"), token))
                    .andExpect(jsonPath("$.totalElements").value(2));

            mockMvc.perform(asUser(get("/api/applications")
                            .param("status", "APPLIED").param("employmentType", "FULL_TIME"), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Zoho"));

            mockMvc.perform(asUser(get("/api/applications").param("workMode", "HYBRID"), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Atlassian"));

            mockMvc.perform(asUser(get("/api/applications").param("tag", "referral"), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Zoho"));

            mockMvc.perform(asUser(get("/api/applications").param("submittedOnly", "true"), token))
                    .andExpect(jsonPath("$.totalElements").value(2));

            // Filters that contradict each other return an empty page, not an error.
            mockMvc.perform(asUser(get("/api/applications")
                            .param("status", "APPLIED").param("workMode", "HYBRID"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("filters by the applied-date range")
        void filtersByDateRange() throws Exception {
            String token = registerAndReturnToken();
            Map<String, Object> recent = newApplication("Recent Co", "Role");
            recent.put("status", "APPLIED");
            recent.put("appliedAt", LocalDate.now().minusDays(2).toString());
            createApplication(token, recent);

            Map<String, Object> old = newApplication("Old Co", "Role");
            old.put("status", "APPLIED");
            old.put("appliedAt", LocalDate.now().minusDays(90).toString());
            createApplication(token, old);

            mockMvc.perform(asUser(get("/api/applications")
                            .param("appliedFrom", LocalDate.now().minusDays(7).toString()), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Recent Co"));

            mockMvc.perform(asUser(get("/api/applications")
                            .param("appliedTo", LocalDate.now().minusDays(30).toString()), token))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].company").value("Old Co"));
        }

        @Test
        @DisplayName("an impossible date range is a 400, not an empty result")
        void rejectsInvertedDateRange() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/applications")
                            .param("appliedFrom", LocalDate.now().toString())
                            .param("appliedTo", LocalDate.now().minusDays(10).toString()), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("never returns another user's rows, whatever the filter")
        void isolationSurvivesFiltering() throws Exception {
            String owner = registerAndReturnToken();
            String other = registerSecondUser();
            createApplication(owner, newApplication("Owner Only Co", "Role"));

            mockMvc.perform(asUser(get("/api/applications").param("search", "Owner"), other))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            mockMvc.perform(asUser(get("/api/applications/filter-options"), other))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companies.length()").value(0));

            mockMvc.perform(asUser(get("/api/applications/filter-options"), owner))
                    .andExpect(jsonPath("$.companies[0]").value("Owner Only Co"));
        }
    }

    // --------------------------------------------------------- sorting & paging

    @Nested
    @DisplayName("sorting and pagination")
    class SortingAndPaging {

        @Test
        @DisplayName("sorts by whitelisted keys in both directions")
        void sortsByKey() throws Exception {
            String token = registerAndReturnToken();
            createApplication(token, newApplication("Beta Corp", "Role"));
            createApplication(token, newApplication("Alpha Corp", "Role"));
            createApplication(token, newApplication("Gamma Corp", "Role"));

            mockMvc.perform(asUser(get("/api/applications").param("sort", "company"), token))
                    .andExpect(jsonPath("$.content[0].company").value("Alpha Corp"))
                    .andExpect(jsonPath("$.content[2].company").value("Gamma Corp"));

            mockMvc.perform(asUser(get("/api/applications").param("sort", "-company"), token))
                    .andExpect(jsonPath("$.content[0].company").value("Gamma Corp"))
                    .andExpect(jsonPath("$.content[2].company").value("Alpha Corp"));
        }

        @Test
        @DisplayName("an unknown sort key is rejected instead of being interpolated into ORDER BY")
        void rejectsUnknownSort() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/applications")
                            .param("sort", "password; DROP TABLE users"), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NEWEST")));
        }

        @Test
        @DisplayName("pages through the result set and reports totals")
        void paginates() throws Exception {
            String token = registerAndReturnToken();
            for (int index = 0; index < 5; index++) {
                createApplication(token, newApplication("Company " + index, "Role " + index));
            }

            mockMvc.perform(asUser(get("/api/applications")
                            .param("page", "1").param("size", "2").param("sort", "company"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].company").value("Company 2"))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.first").value(false))
                    .andExpect(jsonPath("$.last").value(false));
        }

        @Test
        @DisplayName("page size is capped at 100 and negative pages are rejected")
        void rejectsUnreasonablePaging() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/applications").param("size", "500"), token))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(asUser(get("/api/applications").param("page", "-1"), token))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ update

    @Nested
    @DisplayName("PUT /api/applications/{id}")
    class Update {

        @Test
        @DisplayName("updates fields and keeps the audit trail intact")
        void updatesAnApplication() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            Map<String, Object> body = newApplication("Zoho Corporation", "Senior Java Developer");
            body.put("status", "SAVED");
            body.put("location", "Chennai");
            body.put("notes", "Updated");

            mockMvc.perform(asUser(json(put("/api/applications/" + id), body), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.company").value("Zoho Corporation"))
                    .andExpect(jsonPath("$.jobTitle").value("Senior Java Developer"))
                    .andExpect(jsonPath("$.location").value("Chennai"))
                    .andExpect(jsonPath("$.notes").value("Updated"))
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty());

            mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(jsonPath("$.application.company").value("Zoho Corporation"));
        }

        @Test
        @DisplayName("validation applies on update as well as create")
        void validatesOnUpdate() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(json(put("/api/applications/" + id), Map.of("jobTitle", "No company")),
                            token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'company')]").exists());
        }
    }

    // ------------------------------------------------------------------ status

    @Nested
    @DisplayName("PATCH /api/applications/{id}/status")
    class StatusTransitions {

        @Test
        @DisplayName("submitting a draft stamps appliedAt and makes it count in the analytics")
        void submittingADraft() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(jsonPath("$.application.submitted").value(false));

            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", "APPLIED", "note", "Submitted through the career page")),
                            token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPLIED"))
                    .andExpect(jsonPath("$.submitted").value(true))
                    .andExpect(jsonPath("$.appliedAt").value(LocalDate.now().toString()));

            mockMvc.perform(asUser(get("/api/applications/" + id + "/status-history"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    // Entry 0 is the creation itself; the transition is entry 1.
                    .andExpect(jsonPath("$[0].toStatus").value("SAVED"))
                    .andExpect(jsonPath("$[1].fromStatus").value("SAVED"))
                    .andExpect(jsonPath("$[1].toStatus").value("APPLIED"))
                    .andExpect(jsonPath("$[1].note").value("Submitted through the career page"));
        }

        @Test
        @DisplayName("an impossible transition is a 400 that explains what is allowed")
        void rejectsImpossibleTransition() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", "OFFER")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.from").value("SAVED"))
                    .andExpect(jsonPath("$.details.to").value("OFFER"))
                    .andExpect(jsonPath("$.details.allowedValues").doesNotExist())
                    .andExpect(jsonPath("$.details.allowedTargets[?(@ == 'APPLIED')]").exists())
                    .andExpect(jsonPath("$.details.allowedTargets[?(@ == 'WITHDRAWN')]").exists());

            // The record was not touched.
            mockMvc.perform(asUser(get("/api/applications/" + id), token))
                    .andExpect(jsonPath("$.application.status").value("SAVED"));
        }

        @Test
        @DisplayName("re-setting the same status is rejected as a no-op")
        void rejectsNoOpTransition() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", "SAVED")), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a closed application can be reopened and the trail records it")
        void reopensAClosedApplication() throws Exception {
            String token = registerAndReturnToken();
            long id = createApplication(token, newApplication("Zoho", "Java Developer"));

            changeStatus(token, id, "APPLIED");
            changeStatus(token, id, "REJECTED");
            changeStatus(token, id, "INTERVIEW");

            JsonNode history = readJson(mockMvc.perform(
                            asUser(get("/api/applications/" + id + "/status-history"), token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(history).hasSize(4);
            // Entry 0 is the creation of the record: there is no previous status to come from.
            assertThat(history.get(0).path("fromStatus").isMissingNode()).isTrue();
            assertThat(history.get(0).path("toStatus").asText()).isEqualTo("SAVED");
            assertThat(history.get(1).path("fromStatus").asText()).isEqualTo("SAVED");
            assertThat(history.get(1).path("toStatus").asText()).isEqualTo("APPLIED");
            assertThat(history.get(2).path("toStatus").asText()).isEqualTo("REJECTED");
            assertThat(history.get(3).path("fromStatus").asText()).isEqualTo("REJECTED");
            assertThat(history.get(3).path("toStatus").asText()).isEqualTo("INTERVIEW");
        }

        private void changeStatus(String token, long id, String status) throws Exception {
            mockMvc.perform(asUser(json(patch("/api/applications/" + id + "/status"),
                            Map.of("status", status)), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(status));
        }
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("DELETE removes the application and cascades to rounds, reminders and history")
    void deletesWithCascade() throws Exception {
        String token = registerAndReturnToken();
        Map<String, Object> body = newApplication("Zoho", "Java Developer");
        // A draft generates nothing; only an active application gets deadline reminders.
        body.put("status", "APPLIED");
        body.put("appliedAt", LocalDate.now().toString());
        body.put("deadline", LocalDate.now().plusDays(2).toString());
        body.put("nextActionDate", LocalDate.now().plusDays(1).toString());
        body.put("nextAction", "Follow up");
        long id = createApplication(token, body);

        // The deadline and the next action each generate an automatic reminder.
        mockMvc.perform(asUser(get("/api/reminders").param("scope", "ALL"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.source == 'AUTO')]").exists());

        mockMvc.perform(asUser(delete("/api/applications/" + id), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(get("/api/applications/" + id), token))
                .andExpect(status().isNotFound());
        mockMvc.perform(asUser(get("/api/applications/" + id + "/status-history"), token))
                .andExpect(status().isNotFound());
        mockMvc.perform(asUser(get("/api/reminders").param("scope", "ALL"), token))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Deleting twice is a 404, not a 500.
        mockMvc.perform(asUser(delete("/api/applications/" + id), token))
                .andExpect(status().isNotFound());
    }
}
