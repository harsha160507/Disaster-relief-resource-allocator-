package com.disasterrelief.allocator.config;

import com.disasterrelief.allocator.api.ApiSecurityErrorHandler;
import com.disasterrelief.allocator.security.ProfileJwtAuthenticationConverter;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean ApiSecurityErrorHandler apiSecurityErrorHandler() { return new ApiSecurityErrorHandler(); }

    @Bean
    @ConditionalOnProperty(prefix = "security.oauth2", name = "issuer-uri")
    JwtDecoder jwtDecoder(@Value("${security.oauth2.issuer-uri}") String issuer,
            @Value("${security.oauth2.audience:}") String audience,
            @Value("${security.oauth2.jwk-set-uri:}") String jwks) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must both be configured");
        }
        requireSecureEndpoint(issuer);
        NimbusJwtDecoder decoder;
        if (jwks.isBlank()) decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        else {
            requireSecureEndpoint(jwks);
            decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        }
        OAuth2TokenValidator<Jwt> claims = token ->
                token.getExpiresAt() != null && token.getSubject() != null && !token.getSubject().isBlank()
                        && token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required token claims are missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), claims));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decoders,
            ObjectProvider<ProfileJwtAuthenticationConverter> converters, ApiSecurityErrorHandler errors) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r
                    .requestMatchers("/actuator/health", "/public/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/api/v1/inventory/**", "/api/v1/resource-types/**")
                        .hasAnyRole("SYSTEM_ADMIN", "INVENTORY_MANAGER")
                    .requestMatchers("/api/v1/disasters/**")
                        .hasAnyRole("SYSTEM_ADMIN", "DISASTER_COORDINATOR")
                    .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable());
        JwtDecoder decoder = decoders.getIfAvailable();
        if (decoder != null) http.oauth2ResourceServer(o -> o
                .authenticationEntryPoint(errors).accessDeniedHandler(errors)
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converters.getObject())));
        return http.build();
    }

    private void requireSecureEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        boolean loopback = "localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("Identity endpoints require HTTPS except on loopback");
        }
    }
}
