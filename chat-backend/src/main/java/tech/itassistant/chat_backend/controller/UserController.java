package tech.itassistant.chat_backend.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.itassistant.chat_backend.dto.UserDto;

@RestController
@RequestMapping("/api")
@Log4j2
public class UserController {

    /** Returns 401 when nobody is signed in, which is how the web client decides to show the login screen. */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDto userDto = new UserDto(user.getSubject(), user.getEmail(), user.getFullName(), user.getPicture());
        log.info("GET : /api/me : RESPONSE : {}", userDto.email());
        return ResponseEntity.ok(userDto);
    }
}
