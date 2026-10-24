package com.disasterrelief.allocator.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "audit_events")
@Immutable
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AuditEvent extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AuditAction action;
    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private UserProfile actor;
    @Column(length = 4000)
    private String details;
    @Column(name = "organization_id")
    private UUID organizationId;
    @Column(name = "actor_organization_id")
    private UUID actorOrganizationId;
    @Column(name = "actor_roles", length = 256)
    private String actorRoles;
    @Column(nullable = false, length = 24)
    private String source;
    @Column(nullable = false, length = 80)
    private String operation;
    @Column(name = "correlation_id")
    private UUID correlationId;

    public AuditEvent(AuditAction action, String entityType, UUID entityId, UUID organizationId,
            String operation, String details, UserProfile actor, UUID actorOrganizationId,
            String actorRoles, String source, UUID correlationId) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.organizationId = organizationId;
        this.operation = operation;
        this.details = details;
        this.actor = actor;
        this.actorOrganizationId = actorOrganizationId;
        this.actorRoles = actorRoles;
        this.source = source;
        this.correlationId = correlationId;
    }
}
