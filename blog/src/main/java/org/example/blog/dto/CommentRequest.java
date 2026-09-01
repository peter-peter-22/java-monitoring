package org.example.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommentRequest(
        @NotNull @NotBlank
        String body
) {
}
