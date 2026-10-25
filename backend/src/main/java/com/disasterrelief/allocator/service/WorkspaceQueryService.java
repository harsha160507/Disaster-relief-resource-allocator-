package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.api.ApiMapper;
import com.disasterrelief.allocator.api.dto.AdministrationDtos;
import com.disasterrelief.allocator.api.dto.ShipmentDtos;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.security.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceQueryService {
    @PersistenceContext private EntityManager em;
    private final CurrentActor actor;
    private final OrganizationAccess access;

    public record PageView(List<?> content, int page, int size, long totalElements, int totalPages) {}
    public record StockRow(UUID id, UUID locationId, String locationName, UUID resourceTypeId,
            String resourceName, String unitOfMeasure, BigDecimal quantity, BigDecimal reservedQuantity,
            BigDecimal availableQuantity, boolean perishable) {}
    public record RequestRow(Object request, String destinationName, String disasterName, String requesterName) {}
    public record AllocationRow(Object allocation, UUID requestId, String requestReference,
            UUID destinationId, String destinationName, String resourceName, String locationName) {}

    public PageView search(String collection, int page, int size, String query, String status,
            UUID organizationId, UUID locationId, String sort, String direction) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || query.length() > 120) {
            throw new IllegalArgumentException("Invalid search bounds");
        }
        if (!Set.of("createdAt", "name", "quantity", "availableQuantity", "status").contains(sort)
                || !Set.of("asc", "desc").contains(direction)) throw new IllegalArgumentException("Invalid sort");
        UUID scope = scope(organizationId);
        return switch (collection) {
            case "inventory" -> {
                if (!actor.current().isAdministrator() && !actor.current().roles().contains(Role.INVENTORY_MANAGER))
                    throw new AccessDeniedException("Inventory manager access required");
                yield page(Inventory.class, "location.organization.id", "resourceType.name", scope, page, size, query, sort, direction,
                        status, locationId, i -> new StockRow(i.getId(), i.getLocation().getId(), i.getLocation().getName(),
                                i.getResourceType().getId(), i.getResourceType().getName(), i.getResourceType().getUnitOfMeasure(),
                                i.getQuantity(), i.getReservedQuantity(), i.getQuantity().subtract(i.getReservedQuantity()),
                                i.getResourceType().isPerishable()));
            }
            case "shipments" -> page(Shipment.class, "destination.organization.id", "trackingReference", scope, page, size, query, sort, direction, status, null, ShipmentDtos::summary);
            case "organizations" -> {
                requireAdmin();
                yield page(Organization.class, "id", "name", scope, page, size, query, sort, direction, status, null, AdministrationDtos::organization);
            }
            case "users" -> {
                requireAdmin();
                yield page(UserProfile.class, "organization.id", "displayName", scope, page, size, query, sort, direction, status, null, AdministrationDtos::user);
            }
            case "resource-types" -> page(ResourceType.class, null, "name", scope, page, size, query, sort, direction, status, null, ApiMapper::resourceType);
            case "locations" -> page(Location.class, "organization.id", "name", scope, page, size, query, sort, direction, status, null, ApiMapper::location);
            case "disasters" -> page(Disaster.class, "organization.id", "name", scope, page, size, query, sort, direction, status, null, ApiMapper::disaster);
            case "requests" -> page(ResourceRequest.class, "destination.organization.id", "reference", scope, page, size, query, sort, direction, status, null,
                    r -> new RequestRow(ApiMapper.request(r), r.getDestination().getName(), r.getDisaster().getName(), r.getRequestedBy().getDisplayName()));
            case "allocations" -> page(Allocation.class, "requestItem.request.destination.organization.id",
                    "requestItem.request.reference", scope, page, size, query, sort, direction, status, locationId,
                    a -> new AllocationRow(ApiMapper.allocation(a), a.getRequestItem().getRequest().getId(),
                            a.getRequestItem().getRequest().getReference(), a.getRequestItem().getRequest().getDestination().getId(),
                            a.getRequestItem().getRequest().getDestination().getName(), a.getRequestItem().getResourceType().getName(),
                            a.getInventory().getLocation().getName()));
            default -> throw new IllegalArgumentException("Unknown workspace collection");
        };
    }

    public Map<String, Long> overview(UUID organizationId) {
        UUID scope = scope(organizationId);
        var result = new LinkedHashMap<String, Long>();
        result.put("inventoryLines", count(Inventory.class, "location.organization.id", scope, null));
        result.put("locations", count(Location.class, "organization.id", scope, null));
        result.put("openRequests", count(ResourceRequest.class, "destination.organization.id", scope,
                Set.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_FULFILLED,
                        RequestStatus.FULFILLED, RequestStatus.PARTIALLY_DELIVERED)));
        result.put("inTransit", count(Shipment.class, "destination.organization.id", scope, Set.of(ShipmentStatus.DISPATCHED)));
        result.put("delivered", count(Shipment.class, "destination.organization.id", scope, Set.of(ShipmentStatus.DELIVERED)));
        return result;
    }

    private void requireAdmin() {
        if (!actor.current().isAdministrator()) throw new AccessDeniedException("Administrator access required");
    }

    private UUID scope(UUID requested) {
        var identity = actor.current();
        if (requested != null) { access.organization(requested); return requested; }
        return identity.isAdministrator() ? null : identity.organizationId();
    }

    private <T> PageView page(Class<T> type, String ownerPath, String searchPath, UUID scope, int page,
            int size, String query, String sort, String direction, String status, UUID location, Function<T, ?> mapper) {
        var cb = em.getCriteriaBuilder();
        var select = cb.createQuery(type); var root = select.from(type);
        select.where(filters(cb, root, type, ownerPath, searchPath, scope, query, status, location));
        Expression<?> sortBy = root.get("createdAt");
        if (sort.equals("name")) sortBy = path(root, searchPath);
        if (sort.equals("quantity") || sort.equals("availableQuantity")) {
            if (type != Inventory.class) throw new IllegalArgumentException("Quantity sort requires inventory");
            sortBy = sort.equals("quantity") ? root.get("quantity") : cb.diff(root.<BigDecimal>get("quantity"), root.<BigDecimal>get("reservedQuantity"));
        }
        if (sort.equals("status")) {
            if (!Set.of(ResourceRequest.class, Disaster.class, Allocation.class, Shipment.class).contains(type)) throw new IllegalArgumentException("No status sort for this collection");
            sortBy = root.get("status");
        }
        select.orderBy(direction.equals("asc") ? cb.asc(sortBy) : cb.desc(sortBy), cb.desc(root.get("id")));
        var values = em.createQuery(select).setFirstResult(page * size).setMaxResults(size).getResultList();
        var count = cb.createQuery(Long.class); var countRoot = count.from(type);
        count.select(cb.count(countRoot)).where(filters(cb, countRoot, type, ownerPath, searchPath, scope, query, status, location));
        long total = em.createQuery(count).getSingleResult();
        return new PageView(values.stream().map(mapper).toList(), page, size, total, (int) Math.ceil((double) total / size));
    }

    private Predicate[] filters(CriteriaBuilder cb, Root<?> root, Class<?> type, String ownerPath,
            String searchPath, UUID scope, String query, String status, UUID location) {
        var predicates = new ArrayList<Predicate>();
        if (scope != null && ownerPath != null) predicates.add(cb.equal(path(root, ownerPath), scope));
        if (!query.isBlank()) {
            String literal = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            predicates.add(cb.like(cb.lower(path(root, searchPath).as(String.class)), "%" + literal + "%", '\\'));
        }
        if (location != null && type == Inventory.class) predicates.add(cb.equal(root.get("location").get("id"), location));
        if (location != null && type == Allocation.class) predicates.add(cb.equal(root.get("requestItem").get("request").get("destination").get("id"), location));
        if (status != null && !status.isBlank()) {
            if (type == Inventory.class) {
                switch (status) {
                    case "AVAILABLE" -> predicates.add(cb.greaterThan(root.get("quantity"), root.get("reservedQuantity")));
                    case "RESERVED" -> predicates.add(cb.greaterThan(root.get("reservedQuantity"), BigDecimal.ZERO));
                    case "EMPTY" -> predicates.add(cb.equal(root.get("quantity"), BigDecimal.ZERO));
                    default -> throw new IllegalArgumentException("Invalid stock filter");
                }
            } else {
                Object value;
                if (type == ResourceRequest.class) value = RequestStatus.valueOf(status);
                else if (type == Disaster.class) value = DisasterStatus.valueOf(status);
                else if (type == Allocation.class) value = AllocationStatus.valueOf(status);
                else if (type == Shipment.class) value = ShipmentStatus.valueOf(status);
                else throw new IllegalArgumentException("This collection has no status filter");
                predicates.add(cb.equal(root.get("status"), value));
            }
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Path<?> path(Path<?> root, String dotted) {
        Path<?> result = root;
        for (String field : dotted.split("\\.")) result = result.get(field);
        return result;
    }

    private long count(Class<?> type, String owner, UUID scope, Set<?> statuses) {
        var cb = em.getCriteriaBuilder(); var query = cb.createQuery(Long.class); var root = query.from(type);
        var predicates = new ArrayList<Predicate>();
        if (scope != null) predicates.add(cb.equal(path(root, owner), scope));
        if (statuses != null) predicates.add(root.get("status").in(statuses));
        query.select(cb.count(root)).where(predicates.toArray(Predicate[]::new));
        return em.createQuery(query).getSingleResult();
    }
}
