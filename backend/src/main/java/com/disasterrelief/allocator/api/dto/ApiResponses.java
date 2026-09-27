package com.disasterrelief.allocator.api.dto;

import com.disasterrelief.allocator.domain.AllocationStatus;
import com.disasterrelief.allocator.domain.DisasterStatus;
import com.disasterrelief.allocator.domain.DisasterType;
import com.disasterrelief.allocator.domain.RequestPriority;
import com.disasterrelief.allocator.domain.RequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ApiResponses {
    private ApiResponses() { }
    public record DisasterResponse(UUID id, String name, DisasterType type, DisasterStatus status,
            String description, Instant startedAt, Instant endedAt, Set<UUID> affectedLocationIds) { }
    public record LocationResponse(UUID id, String name, String address, String region,
            BigDecimal latitude, BigDecimal longitude, UUID organizationId) { }
    public record ResourceTypeResponse(UUID id, String name, String unitOfMeasure, boolean perishable,
            boolean active) { }
    public record InventoryResponse(UUID id, UUID locationId, UUID resourceTypeId, BigDecimal quantity,
            BigDecimal reservedQuantity, BigDecimal availableQuantity) { }
    public record RequestItemResponse(UUID id, UUID resourceTypeId, BigDecimal requestedQuantity,
            BigDecimal allocatedQuantity) { }
    public record RequestResponse(UUID id, String reference, UUID disasterId, UUID requesterId,
            UUID destinationId, RequestStatus status, RequestPriority priority, String notes,
            List<RequestItemResponse> items) { }
    public record AllocationResponse(UUID id, UUID requestItemId, UUID inventoryId, BigDecimal quantity,
            AllocationStatus status, UUID shipmentId) { }
}