package com.jobtrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it and populates the security context.
 *
 * <p>Behaviour on failure is deliberate:</p>
 * <ul>
 *   <li>No header, or a header that is not a Bearer token -&gt; continue unauthenticated; the
 *       authorization rules answer with {@code 401} (or permit the request if the endpoint is public).</li>
 *   <li>Expired or invalid token -&gt; continue unauthenticated, but remember the reason in a request
 *       attribute so the entry point can tell the client {@code token_expired} instead of a generic
 *       error. This is what lets the SPA silently refresh the session.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute carrying the machine-readable auth failure reason. */
    public static final String ATTR_AUTH_ERROR = "jobtrack.authError";
    public static final String REASON_EXPIRED = "token_expired";
    public static final String REASON_INVALID = "token_invalid";
    public static final String REASON_USER_GONE = "user_not_found";
    public static final String REASON_DISABLED = "account_disabled";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            if (userDetails.isEnabled()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                request.setAttribute(ATTR_AUTH_ERROR, REASON_DISABLED);
                log.debug("Rejected access token of disabled account for request {}",
                        request.getRequestURI());
            }
        } catch (UsernameNotFoundException e) {
            // The account was deleted (by an admin or by the user) after the token was issued.
            request.setAttribute(ATTR_AUTH_ERROR, REASON_USER_GONE);
            log.debug("Rejected access token of deleted account for request {}",
                    request.getRequestURI());
        } catch (ExpiredJwtException e) {
            request.setAttribute(ATTR_AUTH_ERROR, REASON_EXPIRED);
            log.debug("Rejected expired access token for request {}", request.getRequestURI());
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(ATTR_AUTH_ERROR, REASON_INVALID);
            log.debug("Rejected invalid access token for request {}: {}",
                    request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
