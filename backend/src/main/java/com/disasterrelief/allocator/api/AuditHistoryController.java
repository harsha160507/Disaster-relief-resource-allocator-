package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.domain.AuditAction;
import com.disasterrelief.allocator.service.AuditHistoryService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AuditHistoryController {
    private final AuditHistoryService history;

    @GetMapping
    public AuditHistoryService.HistoryPage search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID correlationId) {
        return history.search(page, size, entityType, entityId, actorId, organizationId,
                action, from, to, correlationId);
    }

    @GetMapping("/{id}")
    public AuditHistoryService.EventView get(@PathVariable UUID id) { return history.get(id); }
}
