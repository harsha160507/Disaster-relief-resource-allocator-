package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.security.LocalIdentity;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.request.*;
import tools.jackson.databind.ObjectMapper;

/** Every append participates in the business transaction; attribution is never supplied by the client. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuditWriter {
    private final AuditEventRepository events;
    private final UserProfileRepository users;
    private final ObjectMapper json = new ObjectMapper();

    public void record(AuditAction action, String type, UUID id, UUID organizationId,
            String operation, Map<String, ?> details) {
        append(action, type, id, organizationId, operation, details, false);
    }

    public void bootstrap(UUID id, Map<String, ?> details) {
        append(AuditAction.CREATED, "USER_PROFILE", id, null, "user.bootstrapped", details, true);
    }

    private void append(AuditAction action, String type, UUID id, UUID organizationId,
            String operation, Map<String, ?> details, boolean bootstrap) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        LocalIdentity identity = null;
        if (!bootstrap && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (!(authentication.getPrincipal() instanceof LocalIdentity local)) {
                throw new IllegalStateException("An authenticated audit actor must have a local identity");
            }
            identity = local;
        }
        String serialized = json.writeValueAsString(details);
        if (serialized.length() > 4000) throw new IllegalArgumentException("Audit details exceed the storage limit");
        String source = bootstrap ? "BOOTSTRAP" : identity == null ? "SYSTEM_INTERNAL" : "USER";
        String roles = identity == null ? null : identity.roles().stream().map(Enum::name)
                .sorted().collect(Collectors.joining(","));
        events.saveAndFlush(new AuditEvent(action, type, Objects.requireNonNull(id), organizationId,
                operation, serialized, identity == null ? null : users.getReferenceById(identity.profileId()),
                identity == null ? null : identity.organizationId(), roles, source, correlation()));
    }

    private UUID correlation() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        var request = attributes.getRequest();
        String key = AuditWriter.class.getName() + ".correlationId";
        if (request.getAttribute(key) == null) request.setAttribute(key, UUID.randomUUID());
        return (UUID) request.getAttribute(key);
    }
}
