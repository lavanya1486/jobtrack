package com.jobtrack.security;

/**
 * Non-sensitive request metadata stored alongside a refresh token so a user can recognise (and an
 * admin can audit) which device a session belongs to.
 */
public record RequestMetadata(String userAgent, String ipAddress) {

    public static RequestMetadata unknown() {
        return new RequestMetadata(null, null);
    }

    public RequestMetadata truncated() {
        return new RequestMetadata(
                userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)),
                ipAddress == null ? null : ipAddress.substring(0, Math.min(ipAddress.length(), 64)));
    }
}
