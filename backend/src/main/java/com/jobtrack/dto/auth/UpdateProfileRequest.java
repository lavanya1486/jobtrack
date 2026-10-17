package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Profile update - e-mail is immutable here on purpose (changing it should require verification). */
@Schema(description = "Editable profile fields")
public record UpdateProfileRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters")
        String name,

        @Schema(example = "Asia/Kolkata", description = "IANA time zone id used for reminders")
        @Size(max = 64)
        String timezone,

        @Schema(description = "Opt in to e-mail reminders (requires SMTP to be configured)")
        Boolean emailRemindersEnabled
) {
}
