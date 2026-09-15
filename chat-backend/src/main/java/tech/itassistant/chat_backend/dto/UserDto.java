package tech.itassistant.chat_backend.dto;

/**
 * The signed-in user, as shown in the web client header.
 */
public record UserDto(
        String subject,
        String email,
        String name,
        String picture) {
}
