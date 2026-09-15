package tech.itassistant.mcp_kb_server.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * The MCP server is a separate process, so it does not trust the chat backend blindly:
 * every request must carry the user's access token, which is verified against the OIDC provider.
 * That is what makes the identity recorded by create_article trustworthy.
 */
@Configuration
@EnableWebSecurity
@Log4j2
public class SecurityConfig {

    @Value("${security.oidc.issuer-uri}")
    private String issuerUri;

    @Value("${security.oidc.audience}")
    private String audience;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Signature, issuer and expiry are checked by the default validator; the audience check is added
     * on top, because a token issued for a different service must not be accepted here.
     * withIssuerLocation() reads the discovery document on the first token instead of at startup,
     * so this service does not have to wait for the provider to be up before it can boot.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("jwtDecoder() : Trusting tokens from issuer {} for audience {}", issuerUri, audience);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        claim -> claim != null && claim.contains(audience))));
        return decoder;
    }
}
