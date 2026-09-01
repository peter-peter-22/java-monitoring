package org.example.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record PostRequest(
        @NotNull @NotBlank @Length(max = 200)
        String title,
        @NotNull @NotBlank
        String body
) {
}
