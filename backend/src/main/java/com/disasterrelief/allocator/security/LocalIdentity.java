package com.disasterrelief.allocator.security;

import com.disasterrelief.allocator.domain.Role;
import java.security.Principal;
import java.util.*;

public record LocalIdentity(UUID profileId, String issuer, String subject,
        UUID organizationId, Set<Role> roles) implements Principal {
    public LocalIdentity { roles = Set.copyOf(roles); }
    @Override public String getName() { return issuer + "|" + subject; }
    public boolean isAdministrator() { return roles.contains(Role.SYSTEM_ADMIN); }
}
