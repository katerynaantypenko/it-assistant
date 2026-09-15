package tech.itassistant.chat_backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The user's answer to a pending tool approval.
 */
public record ApprovalRequest(
        @NotNull
        Boolean approved) {
}
