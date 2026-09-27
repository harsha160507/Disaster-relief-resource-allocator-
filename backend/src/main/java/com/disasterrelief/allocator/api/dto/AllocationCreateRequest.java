package com.disasterrelief.allocator.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AllocationCreateRequest(@NotNull UUID requestItemId, @NotNull UUID inventoryId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity) { }