package tech.itassistant.chat_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank
        @Size(max = 4000)
        String message) {
}
