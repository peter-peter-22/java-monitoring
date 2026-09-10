package org.example.blog.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.example.blog.model.AppUser;
import org.example.blog.repository.UserRepository;
import org.example.blog.security.AuthenticatedUserMdcFilter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenTelemetryLogbackAppenderTests {

    @Test
    void exportsLoggerNameTraceSpanAndAuthenticatedUsersSelectedMdcAttribute() throws Exception {
        CapturingLogRecordExporter exporter = new CapturingLogRecordExporter();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(loggerProvider)
                .setTracerProvider(SdkTracerProvider.builder().build())
                .build();

        OpenTelemetryAppender appender = new OpenTelemetryAppender();
        appender.setCaptureMdcAttributes("user.id");
        appender.setOpenTelemetry(openTelemetry);
        appender.start();

        String loggerName = getClass().getName() + ".correlation";
        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(loggerName);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);

        Span span = openTelemetry.getTracer("test").spanBuilder("request").startSpan();
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("alice")).thenReturn(java.util.Optional.of(new AppUser(42L, "alice", "hash")));
        AuthenticatedUserMdcFilter filter = new AuthenticatedUserMdcFilter(users);
        try (Scope ignored = span.makeCurrent()) {
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("alice", "password", "ROLE_USER"));
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) ->
                    logger.info("Created blog post"));
        }
        finally {
            MDC.remove("user.id");
            SecurityContextHolder.clearContext();
            span.end();
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(exporter.records).hasSize(1);
        LogRecordData record = exporter.records.getFirst();
        assertThat(record.getBody().asString()).isEqualTo("Created blog post");
        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo(loggerName);
        assertThat(record.getSpanContext().getTraceId()).isEqualTo(span.getSpanContext().getTraceId());
        assertThat(record.getSpanContext().getSpanId()).isEqualTo(span.getSpanContext().getSpanId());
        assertThat(record.getAttributes().get(AttributeKey.stringKey("user.id"))).isEqualTo("42");

        openTelemetry.close();
    }

    private static final class CapturingLogRecordExporter implements LogRecordExporter {
        private final List<LogRecordData> records = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            records.addAll(logs);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
