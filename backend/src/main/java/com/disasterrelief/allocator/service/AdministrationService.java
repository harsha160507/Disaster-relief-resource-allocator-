package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.api.dto.AdministrationDtos;
import com.disasterrelief.allocator.api.dto.AdministrationDtos.*;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.exception.*;
import com.disasterrelief.allocator.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdministrationService {
    private final OrganizationRepository organizations;
    private final UserProfileRepository users;
    private final AdministrationLockRepository locks;
    @Value("${security.oauth2.issuer-uri:}") private String trustedIssuer;

    @Transactional
    public OrganizationView createOrganization(OrganizationCreate input) {
        lock();
        var organization = new Organization();
        setOrganization(organization, input.name(), input.code(), input.contactEmail());
        return AdministrationDtos.organization(organizations.saveAndFlush(organization));
    }

    @Transactional
    public OrganizationView updateOrganization(UUID id, OrganizationUpdate input) {
        lock();
        var organization = organizationEntity(id);
        version(organization.getVersion(), input.version());
        setOrganization(organization, input.name(), input.code(), input.contactEmail());
        organization.setActive(input.active());
        return AdministrationDtos.organization(organizations.saveAndFlush(organization));
    }

    @Transactional(readOnly = true)
    public OrganizationView organization(UUID id) {
        return AdministrationDtos.organization(organizationEntity(id));
    }

    @Transactional(readOnly = true)
    public PageView<OrganizationView> organizations(int page, int size) {
        var result = organizations.findAll(page(page, size));
        return new PageView<>(result.getContent().stream().map(AdministrationDtos::organization).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public UserView createUser(UserCreate input) {
        lock();
        requireIdentity(input.issuer(), input.subject());
        if (users.findByExternalIssuerAndExternalSubject(input.issuer(), input.subject()).isPresent()) {
            throw new ResourceConflictException("Identity is already provisioned");
        }
        var user = new UserProfile();
        user.setExternalIssuer(input.issuer());
        user.setExternalSubject(input.subject());
        updateMembership(user, input.displayName(), input.email(), input.organizationId(), input.roles(), true);
        return AdministrationDtos.user(users.saveAndFlush(user));
    }

    @Transactional
    public UserView updateUser(UUID id, UserUpdate input) {
        lock();
        var user = userEntity(id);
        version(user.getVersion(), input.version());
        if (effectiveAdmin(user) && (!input.active() || !input.roles().contains(Role.SYSTEM_ADMIN))
                && activeAdministrators() <= 1) {
            throw new BusinessRuleViolationException("The last active system administrator must remain enabled");
        }
        // Identity binding is immutable; provision a replacement identity explicitly.
        updateMembership(user, input.displayName(), input.email(), input.organizationId(), input.roles(), input.active());
        return AdministrationDtos.user(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserView user(UUID id) { return AdministrationDtos.user(userEntity(id)); }

    @Transactional(readOnly = true)
    public PageView<UserView> users(int page, int size) {
        var result = users.findAll(page(page, size));
        return new PageView<>(result.getContent().stream().map(AdministrationDtos::user).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public boolean bootstrap(String subject, String displayName) {
        lock();
        requireIdentity(trustedIssuer, subject);
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("A bootstrap display name of at most 120 characters is required");
        }
        if (activeAdministrators() > 0) return false;
        if (users.findByExternalIssuerAndExternalSubject(trustedIssuer, subject).isPresent()) {
            throw new IllegalStateException("Bootstrap refuses to promote an existing identity");
        }
        var user = new UserProfile();
        user.setExternalIssuer(trustedIssuer);
        user.setExternalSubject(subject);
        user.setDisplayName(displayName.trim());
        user.setRoles(new HashSet<>(Set.of(Role.SYSTEM_ADMIN)));
        users.saveAndFlush(user);
        return true;
    }

    private void lock() {
        locks.lockAdministration().orElseThrow(
                () -> new IllegalStateException("The administrative lock is missing; apply database migrations"));
    }

    private void requireIdentity(String issuer, String subject) {
        if (trustedIssuer.isBlank() || !trustedIssuer.equals(issuer)
                || subject == null || subject.isBlank() || subject.length() > 255 || issuer.length() > 512) {
            throw new BusinessRuleViolationException("Identity must match the configured issuer and a valid subject");
        }
    }

    private void updateMembership(UserProfile user, String name, String email, UUID organizationId,
            Set<Role> roles, boolean active) {
        if (roles == null || roles.isEmpty() || roles.contains(null)) {
            throw new BusinessRuleViolationException("At least one valid role is required");
        }
        boolean administrator = roles.contains(Role.SYSTEM_ADMIN);
        if (administrator && (organizationId != null || roles.size() != 1)) {
            throw new BusinessRuleViolationException("System administrators must have only SYSTEM_ADMIN and no organization");
        }
        if (!administrator && organizationId == null) {
            throw new BusinessRuleViolationException("Organization members require an organization");
        }
        Organization organization = organizationId == null ? null : organizationEntity(organizationId);
        if (active && organization != null && !organization.isActive()) {
            throw new BusinessRuleViolationException("Active users require an active organization");
        }
        // Legacy identities cannot be reactivated without explicit issuer provisioning.
        if (active && (user.getExternalIssuer() == null || !user.getExternalIssuer().equals(trustedIssuer))) {
            throw new BusinessRuleViolationException("Provision a trusted replacement identity before activation");
        }
        user.setDisplayName(name.trim());
        user.setEmail(email == null || email.isBlank() ? null : email.trim());
        user.setOrganization(organization);
        user.setRoles(new HashSet<>(roles));
        user.setActive(active);
    }

    private void setOrganization(Organization organization, String name, String rawCode, String email) {
        String code = rawCode == null || rawCode.isBlank() ? null : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code != null) organizations.findByCodeIgnoreCase(code).ifPresent(existing -> {
            if (!existing.getId().equals(organization.getId())) throw new ResourceConflictException("Organization code exists");
        });
        organization.setName(name.trim());
        organization.setCode(code);
        organization.setContactEmail(email == null || email.isBlank() ? null : email.trim());
    }

    private boolean effectiveAdmin(UserProfile user) {
        return user.isActive() && user.getOrganization() == null
                && trustedIssuer.equals(user.getExternalIssuer()) && user.getRoles().contains(Role.SYSTEM_ADMIN);
    }
    private long activeAdministrators() {
        return users.findByRolesContaining(Role.SYSTEM_ADMIN).stream().filter(this::effectiveAdmin).count();
    }
    private Organization organizationEntity(UUID id) {
        return organizations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }
    private UserProfile userEntity(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User profile not found"));
    }
    private void version(Long actual, Long expected) {
        if (!Objects.equals(actual, expected)) throw new ResourceConflictException("The record has changed; reload before editing");
    }
    private Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be nonnegative; size must be 1 to 100");
        return PageRequest.of(page, size, Sort.by("id"));
    }
}
