package com.disasterrelief.allocator.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record InventoryAdjustRequest(@NotNull BigDecimal change) { }