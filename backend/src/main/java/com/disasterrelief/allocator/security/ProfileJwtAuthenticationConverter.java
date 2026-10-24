package com.disasterrelief.allocator.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final IdentityService identities;
    @Value("${security.oauth2.audience:}") private String audience;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (audience.isBlank() || jwt.getExpiresAt() == null || jwt.getAudience() == null
                || !jwt.getAudience().contains(audience)) {
            throw new BadCredentialsException("Required token audience and expiration are missing");
        }
        LocalIdentity identity = identities.resolve(jwt.getClaimAsString("iss"), jwt.getSubject());
        var authorities = identity.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);
    }
}
