package com.disasterrelief.allocator.security;

import com.disasterrelief.allocator.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityService {
    private final UserProfileRepository users;

    @Transactional(readOnly = true)
    public LocalIdentity resolve(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new BadCredentialsException("A provisioned identity is required");
        }
        var user = users.findByExternalIssuerAndExternalSubject(issuer, subject)
                .orElseThrow(() -> new BadCredentialsException("A provisioned identity is required"));
        var organization = user.getOrganization();
        boolean admin = user.getRoles().contains(com.disasterrelief.allocator.domain.Role.SYSTEM_ADMIN);
        if (!user.isActive() || user.getRoles().isEmpty()
                || (organization != null && !organization.isActive())
                || (!admin && organization == null)
                || (admin && organization != null)) {
            throw new BadCredentialsException("The provisioned identity is inactive or invalid");
        }
        return new LocalIdentity(user.getId(), issuer, subject,
                organization == null ? null : organization.getId(), user.getRoles());
    }
}
