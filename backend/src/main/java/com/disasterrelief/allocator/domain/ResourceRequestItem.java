package com.disasterrelief.allocator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resource_request_items")
@Getter
@Setter
@NoArgsConstructor
public class ResourceRequestItem extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ResourceRequest request;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_type_id", nullable = false)
    private ResourceType resourceType;

    @NotNull
    @DecimalMin(value = "0.001", inclusive = true)
    @Column(name = "requested_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal requestedQuantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "allocated_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal allocatedQuantity = BigDecimal.ZERO;

    @NotNull
    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
