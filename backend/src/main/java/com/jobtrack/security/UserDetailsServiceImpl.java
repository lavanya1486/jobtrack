package com.jobtrack.security;

import com.jobtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts by e-mail for Spring Security.
 *
 * <p>Deliberately throws the same exception for "unknown e-mail" and "disabled account" so the login
 * endpoint cannot be used to enumerate registered addresses.</p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    /** Used by the JWT filter, which resolves the principal by id. */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        return userRepository.findById(id)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Account no longer exists"));
    }
}
