package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.*;
import java.util.Map;

/** Allocated includes goods reserved, in transit and delivered; delivery is tracked separately. */
final class RequestProgress {
    private RequestProgress() {}

    static void update(ResourceRequest request, AuditWriter audit) {
        var items = request.getItems();
        boolean completeDelivery = !items.isEmpty() && items.stream()
                .allMatch(i -> i.getDeliveredQuantity().compareTo(i.getRequestedQuantity()) >= 0);
        boolean anyDelivery = items.stream().anyMatch(i -> i.getDeliveredQuantity().signum() > 0);
        boolean completeAllocation = !items.isEmpty() && items.stream()
                .allMatch(i -> i.getAllocatedQuantity().compareTo(i.getRequestedQuantity()) >= 0);
        boolean anyAllocation = items.stream().anyMatch(i -> i.getAllocatedQuantity().signum() > 0);
        RequestStatus next = completeDelivery ? RequestStatus.DELIVERED
                : anyDelivery ? RequestStatus.PARTIALLY_DELIVERED
                : completeAllocation ? RequestStatus.FULFILLED
                : anyAllocation ? RequestStatus.PARTIALLY_FULFILLED : RequestStatus.APPROVED;
        var before = request.getStatus();
        if (before != next) {
            request.setStatus(next);
            audit.record(AuditAction.STATUS_CHANGED, "RESOURCE_REQUEST", request.getId(),
                    request.getDestination().getOrganization().getId(), "request.fulfillment_changed",
                    Map.of("before", before, "after", next));
        }
    }
}
