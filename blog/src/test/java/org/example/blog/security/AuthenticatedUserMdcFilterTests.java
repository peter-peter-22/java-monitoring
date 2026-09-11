package org.example.blog.security;

import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.Tracer;
import org.example.blog.model.AppUser;
import org.example.blog.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthenticatedUserMdcFilterTests {

    private final UserRepository users = mock(UserRepository.class);
    private final Tracer tracer = mock(Tracer.class);
    private final SpanCustomizer spanCustomizer = mock(SpanCustomizer.class);
    private final AuthenticatedUserMdcFilter filter = new AuthenticatedUserMdcFilter(users, tracer);

    @AfterEach
    void clearThreadLocals() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void addsTheAuthenticatedUsersDatabaseIdOnlyForTheRemainingRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", "password", "ROLE_USER"));
        when(users.findByUsername("alice"))
                .thenReturn(Optional.of(new AppUser(42L, "alice", "hash")));
        when(tracer.currentSpanCustomizer()).thenReturn(spanCustomizer);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) ->
                assertThat(MDC.get(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY)).isEqualTo("42"));

        assertThat(MDC.get(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY)).isNull();
        verify(users).findByUsername("alice");
        verify(tracer).currentSpanCustomizer();
        verify(spanCustomizer).tag(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY, "42");
    }

    @Test
    void omitsUserIdForAnonymousRequests() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MDC.put(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY, "stale-value");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) ->
                assertThat(MDC.get(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY)).isNull());

        assertThat(MDC.get(AuthenticatedUserMdcFilter.USER_ID_MDC_KEY)).isNull();
        verifyNoInteractions(users);
        verifyNoInteractions(tracer, spanCustomizer);
    }
}
