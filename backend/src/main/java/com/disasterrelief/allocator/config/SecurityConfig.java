package com.disasterrelief.allocator.config;

import com.disasterrelief.allocator.api.ApiSecurityErrorHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    ApiSecurityErrorHandler apiSecurityErrorHandler() {
        return new ApiSecurityErrorHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.oauth2", name = "issuer-uri")
    JwtDecoder jwtDecoder(@Value("${security.oauth2.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    @Order(1)
    @ConditionalOnBean(JwtDecoder.class)
        SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            ApiSecurityErrorHandler securityErrors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(this::authorizeRequests)
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(securityErrors)
                    .accessDeniedHandler(securityErrors))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @Order(2)
    @ConditionalOnMissingBean(JwtDecoder.class)
        SecurityFilterChain protectedFallbackSecurityFilterChain(HttpSecurity http,
            ApiSecurityErrorHandler securityErrors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(this::authorizeRequests)
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(securityErrors)
                    .accessDeniedHandler(securityErrors))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    private void authorizeRequests(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requests) {
        requests
            .requestMatchers("/actuator/health", "/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/v1/inventory/**", "/api/v1/resource-types/**")
                    .hasAnyRole("SYSTEM_ADMIN", "INVENTORY_MANAGER")
                .requestMatchers("/api/v1/disasters/**")
                    .hasAnyRole("SYSTEM_ADMIN", "DISASTER_COORDINATOR")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}