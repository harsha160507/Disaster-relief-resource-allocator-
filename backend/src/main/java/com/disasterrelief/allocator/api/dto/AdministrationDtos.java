package com.disasterrelief.allocator.api.dto;

import com.disasterrelief.allocator.domain.*;
import jakarta.validation.constraints.*;
import java.util.*;

public final class AdministrationDtos {
    private AdministrationDtos() {}

    public record OrganizationCreate(@NotBlank @Size(max=160) String name,
            @Size(max=80) String code, @Email @Size(max=255) String contactEmail) {}
    public record OrganizationUpdate(@NotBlank @Size(max=160) String name,
            @Size(max=80) String code, @Email @Size(max=255) String contactEmail,
            @NotNull Boolean active, @NotNull @PositiveOrZero Long version) {}
    public record UserCreate(@NotBlank @Size(max=512) String issuer,
            @NotBlank @Size(max=255) String subject,
            @NotBlank @Size(max=120) String displayName, @Email @Size(max=255) String email,
            UUID organizationId, @NotEmpty Set<@NotNull Role> roles) {}
    public record UserUpdate(@NotBlank @Size(max=120) String displayName,
            @Email @Size(max=255) String email, UUID organizationId,
            @NotEmpty Set<@NotNull Role> roles, @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {}
    public record OrganizationView(UUID id, String name, String code, String contactEmail,
            boolean active, Long version) {}
    public record UserView(UUID id, String issuer, String subject, String displayName,
            String email, UUID organizationId, Set<Role> roles, boolean active, Long version) {}
    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    public static OrganizationView organization(Organization value) {
        return new OrganizationView(value.getId(), value.getName(), value.getCode(),
                value.getContactEmail(), value.isActive(), value.getVersion());
    }
    public static UserView user(UserProfile value) {
        return new UserView(value.getId(), value.getExternalIssuer(), value.getExternalSubject(),
                value.getDisplayName(), value.getEmail(),
                value.getOrganization() == null ? null : value.getOrganization().getId(),
                Set.copyOf(value.getRoles()), value.isActive(), value.getVersion());
    }
}
