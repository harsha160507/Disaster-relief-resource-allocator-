package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    List<AuditEvent> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
