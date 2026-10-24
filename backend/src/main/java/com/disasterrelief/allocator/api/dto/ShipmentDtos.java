package com.disasterrelief.allocator.api.dto;

import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.api.ApiMapper;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record Create(@NotNull UUID destinationId,
            @NotBlank @Size(max = 255) String trackingReference,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> allocationIds) {}
    public record Summary(UUID id, UUID destinationId, ShipmentStatus status, String trackingReference,
            Instant dispatchedAt, Instant deliveredAt, Long version) {}
    public record View(UUID id, UUID destinationId, ShipmentStatus status, String trackingReference,
            Instant dispatchedAt, Instant deliveredAt, Long version,
            List<ApiResponses.AllocationResponse> allocations) {}
    public record PageView(List<Summary> content, int page, int size, long totalElements, int totalPages) {}

    public static Summary summary(Shipment s) {
        return new Summary(s.getId(), s.getDestination().getId(), s.getStatus(), s.getTrackingReference(),
                s.getDispatchedAt(), s.getDeliveredAt(), s.getVersion());
    }
    public static View view(Shipment s) {
        return new View(s.getId(), s.getDestination().getId(), s.getStatus(), s.getTrackingReference(),
                s.getDispatchedAt(), s.getDeliveredAt(), s.getVersion(),
                s.getAllocations().stream().sorted(Comparator.comparing(a -> a.getId().toString()))
                        .map(ApiMapper::allocation).toList());
    }
}
