package com.jobtrack.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
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
 * Administration and profile endpoints.
 *
 * <p>Covers the role gate on every {@code /api/admin} route, the cascade behind account deletion,
 * the guards that stop an admin from locking everybody out, and the profile routes that decide how
 * reminder times are rendered.</p>
 */
@DisplayName("Admin & profile API")
class AdminApiIT extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123";

    /** Signs in with the real API and returns the access token. */
    private String login(String email, String password) throws Exception {
        return readJson(mockMvc.perform(json(post("/api/auth/login"),
                                Map.of("email", email, "password", password)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("accessToken").asText();
    }

    /** Registers an ADMIN and returns its access token, keeping the e-mail for id lookups. */
    private String adminToken(String email) throws Exception {
        JsonNode auth = register(email, PASSWORD, "Ada Admin");
        promoteToAdmin(email);
        return auth.path("accessToken").asText();
    }

    private long idOf(String token) throws Exception {
        return readJson(mockMvc.perform(asUser(get("/api/users/me"), token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    /** Finds one account inside the admin directory by e-mail. */
    private JsonNode directoryEntry(String adminToken, String email) throws Exception {
        JsonNode users = readJson(mockMvc.perform(asUser(get("/api/admin/users"), adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode user : users) {
            if (email.equalsIgnoreCase(user.path("email").asText())) {
                return user;
            }
        }
        throw new AssertionError("No directory entry for " + email + " in " + users);
    }

    private long createApplication(String token, String company, String status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("company", company);
        body.put("jobTitle", "Backend Engineer");
        body.put("status", status);
        if (!"SAVED".equals(status)) {
            body.put("appliedAt", LocalDate.now().minusDays(2).toString());
            body.put("deadline", LocalDate.now().plusDays(5).toString());
        }
        return readJson(mockMvc.perform(asUser(json(post("/api/applications"), body), token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    // ------------------------------------------------------------------ the gate

    @Nested
    @DisplayName("role gate")
    class Access {

        @Test
        @DisplayName("every admin route answers 403 for an ordinary USER and 401 when anonymous")
        void adminRoutesAreClosedToUsers() throws Exception {
            String user = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/admin/users"), user))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("forbidden"));
            mockMvc.perform(asUser(get("/api/admin/stats"), user))
                    .andExpect(status().isForbidden());
            mockMvc.perform(asUser(json(patch("/api/admin/users/1/role"), Map.of("role", "ADMIN")), user))
                    .andExpect(status().isForbidden());
            mockMvc.perform(asUser(delete("/api/admin/users/1"), user))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("unauthorized"))
                    .andExpect(jsonPath("$.details.reason").value("missing_credentials"));
        }

        @Test
        @DisplayName("an ADMIN reaches the same routes")
        void adminRoutesAreOpenToAdmins() throws Exception {
            String admin = adminToken(uniqueEmail());

            mockMvc.perform(asUser(get("/api/admin/users"), admin)).andExpect(status().isOk());
            mockMvc.perform(asUser(get("/api/admin/stats"), admin)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("the role is read per request, so a promotion takes effect immediately")
        void promotionAppliesToTheExistingToken() throws Exception {
            String userEmail = uniqueEmail();
            String token = accessToken(userEmail, PASSWORD);

            mockMvc.perform(asUser(get("/api/admin/users"), token)).andExpect(status().isForbidden());

            promoteToAdmin(userEmail);

            mockMvc.perform(asUser(get("/api/admin/users"), token)).andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------------- the directory

    @Nested
    @DisplayName("GET /api/admin/users and /api/admin/stats")
    class Directory {

        @Test
        @DisplayName("the directory counts each account's activity")
        void listsUsersWithActivityCounters() throws Exception {
            String admin = adminToken(uniqueEmail());
            String memberEmail = uniqueEmail();
            String member = accessToken(memberEmail, PASSWORD);
            long appId = createApplication(member, "Flipkart", "APPLIED");

            mockMvc.perform(asUser(json(post("/api/applications/" + appId + "/interviews"),
                            Map.of("roundType", "TECHNICAL",
                                    "scheduledAt", java.time.Instant.now().plusSeconds(172800).toString())),
                    member))
                    .andExpect(status().isCreated());

            JsonNode entry = directoryEntry(admin, memberEmail);
            assertThat(entry.path("applications").asLong()).isEqualTo(1);
            assertThat(entry.path("interviews").asLong()).isEqualTo(1);
            // deadline reminder + interview preparation reminder
            assertThat(entry.path("openReminders").asLong()).isEqualTo(2);
            assertThat(entry.path("role").asText()).isEqualTo("USER");
            assertThat(entry.path("enabled").asBoolean()).isTrue();
            assertThat(entry.has("passwordHash")).isFalse();
            assertThat(entry.has("lastLoginAt")).isFalse();
        }

        @Test
        @DisplayName("platform stats add up across accounts")
        void statsReflectTheWholePlatform() throws Exception {
            String admin = adminToken(uniqueEmail());
            String member = registerSecondUser();
            createApplication(member, "Flipkart", "APPLIED");
            createApplication(member, "Zoho", "SAVED");

            JsonNode stats = readJson(mockMvc.perform(asUser(get("/api/admin/stats"), admin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(stats.path("totalUsers").asLong()).isEqualTo(2);
            assertThat(stats.path("activeUsers").asLong()).isEqualTo(2);
            assertThat(stats.path("adminUsers").asLong()).isEqualTo(1);
            assertThat(stats.path("totalApplications").asLong()).isEqualTo(2);
            assertThat(stats.path("submittedApplications").asLong()).isEqualTo(1);
            assertThat(stats.path("totalInterviews").asLong()).isZero();
            assertThat(stats.path("openReminders").asLong()).isEqualTo(1);
            assertThat(stats.path("averageApplicationsPerUser").asDouble()).isEqualTo(1.0d);
        }
    }

    // ------------------------------------------------------------- role changes

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/role")
    class RoleChanges {

        @Test
        @DisplayName("promoting a user revokes their refresh tokens so other devices re-authenticate")
        void promotesAndRevokesSessions() throws Exception {
            String admin = adminToken(uniqueEmail());
            String memberEmail = uniqueEmail();
            JsonNode auth = register(memberEmail, PASSWORD, "Bo Candidate");
            long memberId = auth.path("user").path("id").asLong();

            mockMvc.perform(asUser(json(patch("/api/admin/users/" + memberId + "/role"),
                            Map.of("role", "ADMIN")), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            // the refresh token issued at registration is dead after a role change
            mockMvc.perform(json(post("/api/auth/refresh"),
                            Map.of("refreshToken", auth.path("refreshToken").asText())))
                    .andExpect(status().isUnauthorized());

            // ...but a fresh login carries the new role
            String fresh = login(memberEmail, PASSWORD);
            mockMvc.perform(asUser(get("/api/admin/stats"), fresh)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("an admin cannot change their own role")
        void refusesSelfRoleChange() throws Exception {
            String adminEmail = uniqueEmail();
            String admin = adminToken(adminEmail);
            long adminId = idOf(admin);

            mockMvc.perform(asUser(json(patch("/api/admin/users/" + adminId + "/role"),
                            Map.of("role", "USER")), admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"));
        }

        @Test
        @DisplayName("the role can be handed over: promote a second admin, then demote the first")
        void handsTheRoleOver() throws Exception {
            String admin = adminToken(uniqueEmail());
            long adminId = idOf(admin);
            String second = accessToken(uniqueEmail(), PASSWORD);
            long secondId = idOf(second);

            mockMvc.perform(asUser(json(patch("/api/admin/users/" + secondId + "/role"),
                            Map.of("role", "ADMIN")), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            // nobody may demote themselves, so the new admin performs the hand-over
            mockMvc.perform(asUser(json(patch("/api/admin/users/" + adminId + "/role"),
                            Map.of("role", "USER")), second))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));

            // the demoted admin loses access immediately, the new one keeps it
            mockMvc.perform(asUser(get("/api/admin/users"), admin))
                    .andExpect(status().isForbidden());
            mockMvc.perform(asUser(get("/api/admin/users"), second))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("setting a role the user already has is rejected")
        void refusesANoopRoleChange() throws Exception {
            String admin = adminToken(uniqueEmail());
            String member = registerSecondUser();
            long memberId = idOf(member);

            mockMvc.perform(asUser(json(patch("/api/admin/users/" + memberId + "/role"),
                            Map.of("role", "USER")), admin))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("unknown ids and unknown role values are rejected")
        void validatesTheRequest() throws Exception {
            String admin = adminToken(uniqueEmail());

            mockMvc.perform(asUser(json(patch("/api/admin/users/999999/role"),
                            Map.of("role", "ADMIN")), admin))
                    .andExpect(status().isNotFound());

            mockMvc.perform(asUser(json(patch("/api/admin/users/1/role"), Map.of()), admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"));

            mockMvc.perform(asUser(json(patch("/api/admin/users/1/role"),
                            Map.of("role", "SUPERUSER")), admin))
                    .andExpect(status().isBadRequest());
        }
    }

    // --------------------------------------------------------------- deletion

    @Nested
    @DisplayName("DELETE /api/admin/users/{id}")
    class Deletion {

        @Test
        @DisplayName("deleting an account cascades to its data and kills its sessions")
        void deletesTheAccountAndEverythingItOwns() throws Exception {
            String admin = adminToken(uniqueEmail());
            String memberEmail = uniqueEmail();
            JsonNode auth = register(memberEmail, PASSWORD, "Bo Candidate");
            String member = auth.path("accessToken").asText();
            long memberId = auth.path("user").path("id").asLong();
            createApplication(member, "Flipkart", "APPLIED");

            mockMvc.perform(asUser(delete("/api/admin/users/" + memberId), admin))
                    .andExpect(status().isNoContent());

            // the account is gone, and so is the data it owned
            mockMvc.perform(asUser(get("/api/users/me"), member))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.details.reason").value("user_not_found"));
            mockMvc.perform(json(post("/api/auth/login"),
                            Map.of("email", memberEmail, "password", PASSWORD)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(json(post("/api/auth/refresh"),
                            Map.of("refreshToken", auth.path("refreshToken").asText())))
                    .andExpect(status().isUnauthorized());

            JsonNode stats = readJson(mockMvc.perform(asUser(get("/api/admin/stats"), admin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(stats.path("totalUsers").asLong()).isEqualTo(1);
            assertThat(stats.path("totalApplications").asLong()).isZero();
            assertThat(stats.path("openReminders").asLong()).isZero();
        }

        @Test
        @DisplayName("an admin cannot delete themselves, the last admin, or a stranger")
        void refusesDangerousDeletions() throws Exception {
            String adminEmail = uniqueEmail();
            String admin = adminToken(adminEmail);
            long adminId = idOf(admin);

            mockMvc.perform(asUser(delete("/api/admin/users/" + adminId), admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"));

            mockMvc.perform(asUser(delete("/api/admin/users/999999"), admin))
                    .andExpect(status().isNotFound());

            // a regular user can still be removed, and the admin account survives
            String memberEmail = uniqueEmail();
            String member = accessToken(memberEmail, PASSWORD);
            mockMvc.perform(asUser(delete("/api/admin/users/" + idOf(member)), admin))
                    .andExpect(status().isNoContent());
            mockMvc.perform(asUser(get("/api/users/me"), member))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(asUser(get("/api/users/me"), admin))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- profile

    @Nested
    @DisplayName("GET|PATCH /api/users/me and POST /api/users/me/password")
    class Profile {

        @Test
        @DisplayName("the profile exposes settings but never the password hash")
        void readsTheProfile() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(get("/api/users/me"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.name").value("Test Candidate"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.timezone").value("UTC"))
                    .andExpect(jsonPath("$.emailRemindersEnabled").value(false));

            JsonNode me = readJson(mockMvc.perform(asUser(get("/api/users/me"), token))
                    .andReturn().getResponse().getContentAsString());
            assertThat(me.has("passwordHash")).isFalse();
            assertThat(me.has("lastLoginAt")).isFalse();
        }

        @Test
        @DisplayName("profile updates change the name, time zone and e-mail opt-in")
        void updatesTheProfile() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(json(patch("/api/users/me"),
                            Map.of("name", "  Priya Nair  ", "timezone", "Asia/Kolkata",
                                    "emailRemindersEnabled", true)), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Priya Nair"))
                    .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                    .andExpect(jsonPath("$.emailRemindersEnabled").value(true));

            // the time zone drives every rendered reminder time
            long appId = createApplication(token, "Zoho", "APPLIED");
            JsonNode reminders = readJson(mockMvc.perform(
                            asUser(get("/api/reminders").param("scope", "ALL"), token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(reminders.path("content").get(0).path("timezone").asText())
                    .isEqualTo("Asia/Kolkata");
            assertThat(reminders.path("content").get(0).path("dueAtLocal").asText())
                    .endsWith("+05:30");
            assertThat(appId).isPositive();
        }

        @Test
        @DisplayName("an unknown time zone or a blank name is rejected")
        void validatesProfileUpdates() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(json(patch("/api/users/me"),
                            Map.of("name", "Test Candidate", "timezone", "Mars/Olympus")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"))
                    .andExpect(jsonPath("$.details.parameter").value("timezone"));

            mockMvc.perform(asUser(json(patch("/api/users/me"), Map.of("name", " ")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

            mockMvc.perform(asUser(json(patch("/api/users/me"),
                            Map.of("name", "Test Candidate", "role", "ADMIN")), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("changing the password signs every other device out")
        void changesThePassword() throws Exception {
            JsonNode auth = register();
            String token = auth.path("accessToken").asText();

            mockMvc.perform(asUser(json(post("/api/users/me/password"),
                            Map.of("currentPassword", PASSWORD, "newPassword", "Rotated99")), token))
                    .andExpect(status().isNoContent());

            // old refresh token revoked, old password rejected, new password accepted
            mockMvc.perform(json(post("/api/auth/refresh"),
                            Map.of("refreshToken", auth.path("refreshToken").asText())))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(json(post("/api/auth/login"),
                            Map.of("email", email, "password", PASSWORD)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(json(post("/api/auth/login"),
                            Map.of("email", email, "password", "Rotated99")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a password change needs the current password and a genuinely new one")
        void validatesPasswordChanges() throws Exception {
            String token = registerAndReturnToken();

            mockMvc.perform(asUser(json(post("/api/users/me/password"),
                            Map.of("currentPassword", "WrongPass1", "newPassword", "Rotated99")), token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.details.reason").value("invalid_credentials"));

            mockMvc.perform(asUser(json(post("/api/users/me/password"),
                            Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD)), token))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(asUser(json(post("/api/users/me/password"),
                            Map.of("currentPassword", PASSWORD, "newPassword", "allletters")), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"));
        }

        @Test
        @DisplayName("profile routes require a token")
        void profileIsNotAnonymous() throws Exception {
            mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(patch("/api/users/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/users/me/password")).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------- one user's data

    @Nested
    @DisplayName("isolation from the admin side")
    class Isolation {

        @Test
        @DisplayName("an admin still cannot reach another user's applications through the user API")
        void adminRoleDoesNotLeakUserData() throws Exception {
            String admin = adminToken(uniqueEmail());
            String member = registerSecondUser();
            long appId = createApplication(member, "Flipkart", "APPLIED");

            mockMvc.perform(asUser(get("/api/applications/" + appId), admin))
                    .andExpect(status().isNotFound());
            Map<String, Object> stolen = new HashMap<>();
            stolen.put("company", "Hacked");
            stolen.put("jobTitle", "Backend Engineer");
            stolen.put("status", "APPLIED");
            stolen.put("appliedAt", LocalDate.now().toString());
            mockMvc.perform(asUser(json(put("/api/applications/" + appId), stolen), admin))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(delete("/api/applications/" + appId), admin))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asUser(get("/api/applications"), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
