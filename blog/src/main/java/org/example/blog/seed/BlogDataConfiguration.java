package org.example.blog.seed;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "blog.data")
public record BlogDataConfiguration(
        boolean enabled,
        @Min(value = 0, message = "users must not be negative") int users,
        @Min(value = 0, message = "posts must not be negative") int posts,
        @Min(value = 0, message = "comments must not be negative") int comments) {
}
