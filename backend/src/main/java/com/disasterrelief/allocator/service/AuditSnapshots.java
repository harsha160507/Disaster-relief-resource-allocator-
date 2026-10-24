package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.*;
import java.util.*;

/** Explicit allowlists: never serialize entities, tokens, identities or personal free text. */
final class AuditSnapshots {
    private AuditSnapshots() {}

    static Map<String, Object> user(UserProfile user) {
        var state = new LinkedHashMap<String, Object>();
        state.put("active", user.isActive());
        state.put("organizationId", user.getOrganization() == null ? null : user.getOrganization().getId());
        state.put("roles", user.getRoles().stream().map(Enum::name).sorted().toList());
        return state;
    }

    static Map<String, Object> organization(Organization organization) {
        return Map.of("active", organization.isActive());
    }

    static Map<String, Object> change(Map<String, Object> before, Map<String, Object> after,
            List<String> changedFields) {
        return Map.of("before", before, "after", after, "changedFields", changedFields);
    }

    static List<String> changed(Object... pairs) {
        var fields = new ArrayList<String>();
        for (int i = 0; i < pairs.length; i += 3) {
            if (!Objects.equals(pairs[i + 1], pairs[i + 2])) fields.add((String) pairs[i]);
        }
        return fields;
    }
}
