package com.disasterrelief.allocator.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    public LocalIdentity current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LocalIdentity identity)) {
            throw new AccessDeniedException("A provisioned identity is required");
        }
        return identity;
    }
}
