# Simple Logging Facade for Java

SLF4J is the logging API used by the application; it lets code log without
depending on a particular logging engine. Logback is the engine that handles
those events here, writing them to the console and exporting them through OTLP
as configured in `logback-spring.xml`.

Use parameterized messages: values are only formatted when the log level is
enabled.

```java
private static final Logger log = LoggerFactory.getLogger(MyService.class);

log.info("Created post id={} by user={}", postId, username);
log.warn("Post id={} was not found", postId);
log.error("Could not publish post id={}", postId, exception);
```

Lombok's `@Slf4j` creates the same `log` field, avoiding the boilerplate:

```java
@Service
@Slf4j
class CommentService {
    void save(Comment comment) {
        log.info("Created comment id={}", comment.getId());
    }
}
```

## MDC context

SLF4J's Mapped Diagnostic Context (MDC) attaches key/value context to every
log event on the current thread. This application adds the authenticated user
as `user.id`; Logback exports that attribute with each OTLP log record.

```java
try {
    MDC.put("user.id", userId);
    log.info("Saving comment"); // includes user.id
} finally {
    MDC.remove("user.id");
}
```

Always clear MDC in a `finally` block: server threads are reused, and MDC does
not automatically propagate to asynchronous work.
