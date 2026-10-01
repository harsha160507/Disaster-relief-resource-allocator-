package com.disasterrelief.allocator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent extends BaseEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @NotBlank
    @Size(max = 80)
    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserProfile actor;

    @Size(max = 4000)
    @Column(length = 4000)
    private String details;
}
