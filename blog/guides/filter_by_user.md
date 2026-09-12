# User logs and traces

The user id is appended to logs and traces to make it possible to retrieve
all actions of a user chronologically.

## Implementation

- The user id is added to the current (java) thread local SLF4J logger context ([MDC](slf4j.md#mdc-context)).
- The current trace span is retrieved and edited to contain the user id. 

```java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.example.blog.repository.UserRepository;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Adds the authenticated application's numeric user identifier to log events and the request trace.
 * `OncePerRequestFilter` is synonymous to request middleware.
 */
@RequiredArgsConstructor
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {
    /** The name of the added user id field in the traces and logs. We can use this field name in the logQL and traceQL queries. */
    static final String USER_ID_MDC_KEY = "user.id";

    private final UserRepository users;

    /** The trace span of this function. */
    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticatedUserId().ifPresentOrElse(
                    userId -> {
                        // Add a parameter of the slf4j logger of this thread.
                        MDC.put(USER_ID_MDC_KEY, userId);
                        // Add a tag to the tracer. (we should avoid "labels" for high-cardinality parameters so we use "tags" instead)
                        tracer.currentSpanCustomizer().tag(USER_ID_MDC_KEY, userId);
                    },
                    // The MDC parameters are (java) thread-local, and they are not cleared after the request is finished.
                    // This is because tomcat is reusing these threads.
                    // We must manually clear them in this middleware to prevent them from affecting the next request.
                    // This also means that async functions won't inherit the user id.
                    () -> MDC.remove(USER_ID_MDC_KEY));
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }

    /** Get the user id from the spring security authentication context if exists. */
    private Optional<String> authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return java.util.Optional.empty();
        }
        // We are using the default username and password spring security user class that does not contain the database id.
        // As a result, we have to get the user id from the username with a database query.
        return users.findByUsername(authentication.getName())
                .map(user -> user.getId().toString());
    }
}
```

# Filtering logs

The `grafana/explore/loki` menu can query the logs and filter by user id.

[Log query guide](logging.md)

![logs.png](images/filter_by_user/logs.png)

# Filtering traces

The `grafana/explore/tempo` menu can query the traces and filter by user id.

[Trace query guide](tracing.md#tracing-query-language)

![traces.png](images/filter_by_user/traces.png)