package org.example.blog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.blog.repository.UserRepository;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds the authenticated application's numeric user identifier to log events for one request.
 */
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {
    static final String USER_ID_MDC_KEY = "user.id";

    private final UserRepository users;

    public AuthenticatedUserMdcFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticatedUserId().ifPresentOrElse(
                    userId -> MDC.put(USER_ID_MDC_KEY, userId),
                    () -> MDC.remove(USER_ID_MDC_KEY));
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }

    private java.util.Optional<String> authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return java.util.Optional.empty();
        }
        return users.findByUsername(authentication.getName())
                .map(user -> user.getId().toString());
    }
}
