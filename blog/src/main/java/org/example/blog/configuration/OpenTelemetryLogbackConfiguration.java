package org.example.blog.configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Installs Spring Boot's configured OpenTelemetry SDK into the Logback bridge. */
@Configuration
public class OpenTelemetryLogbackConfiguration {

    @Bean
    ApplicationRunner installOpenTelemetryLogbackAppender(OpenTelemetry openTelemetry) {
        return arguments -> OpenTelemetryAppender.install(openTelemetry);
    }
}
