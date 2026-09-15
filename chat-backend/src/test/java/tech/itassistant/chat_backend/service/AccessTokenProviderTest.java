package tech.itassistant.chat_backend.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The token handed to the MCP server has to be a valid one, and the failure to produce it has to be
 * recognisable as "sign in again" rather than a generic server error - both are checked here without
 * an identity provider in the picture.
 */
class AccessTokenProviderTest {

    private static final String REGISTRATION_ID = "oidc";

    private OAuth2AuthorizedClientManager authorizedClientManager;
    private AccessTokenProvider accessTokenProvider;
    private OAuth2AuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
        accessTokenProvider = new AccessTokenProvider(authorizedClientManager);
        authentication = new OAuth2AuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("sub", "user-1"), "sub"), List.of(), REGISTRATION_ID);
    }

    @Test
    void handsOutTheTokenTheClientManagerConsidersValid() {
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
                .thenReturn(authorizedClientWith("fresh-token"));

        String token = accessTokenProvider.tokenFor(
                authentication, mock(HttpServletRequest.class), mock(HttpServletResponse.class));

        assertThat(token).isEqualTo("fresh-token");
    }

    @Test
    void reportsAFailedRenewalAsUnauthorized() {
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
                .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

        assertThatThrownBy(() -> accessTokenProvider.tokenFor(
                authentication, mock(HttpServletRequest.class), mock(HttpServletResponse.class)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reportsAMissingAuthorizationAsUnauthorized() {
        // Nothing left to refresh from: the client manager answers with null rather than failing.
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> accessTokenProvider.tokenFor(
                authentication, mock(HttpServletRequest.class), mock(HttpServletResponse.class)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    private OAuth2AuthorizedClient authorizedClientWith(String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId("it-assistant-web")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/login/oauth2/code/oidc")
                .authorizationUri("http://localhost:8180/auth")
                .tokenUri("http://localhost:8180/token")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                tokenValue, Instant.now(), Instant.now().plusSeconds(300));
        return new OAuth2AuthorizedClient(registration, "user-1", accessToken);
    }
}
