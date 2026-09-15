package tech.itassistant.chat_backend.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gives out the access token that the MCP server is called with.
 *
 * <p>The token is short lived, so it is looked up per request rather than kept anywhere: the client
 * manager returns the stored one while it is still valid and silently exchanges the refresh token
 * for a new one when it is not. Renewal writes the new token back into the session, which only works
 * on the request thread - that is why the token is resolved in the controller and passed to the
 * worker thread as a plain string.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AccessTokenProvider {

    private static final String EXPIRED_MESSAGE = "The session has expired. Please sign in again.";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public String tokenFor(OAuth2AuthenticationToken authentication,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(authentication.getAuthorizedClientRegistrationId())
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();

        OAuth2AuthorizedClient authorizedClient;
        try {
            authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        } catch (OAuth2AuthorizationException e) {
            // The refresh token itself has expired or was revoked, so nothing can be renewed here.
            log.warn("tokenFor() : Could not renew the token of {} : {}", authentication.getName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, EXPIRED_MESSAGE, e);
        }

        if (authorizedClient == null) {
            // The session outlived the tokens: there is nothing left to refresh from.
            log.warn("tokenFor() : No authorized client left for {}", authentication.getName());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, EXPIRED_MESSAGE);
        }

        return authorizedClient.getAccessToken().getTokenValue();
    }
}
