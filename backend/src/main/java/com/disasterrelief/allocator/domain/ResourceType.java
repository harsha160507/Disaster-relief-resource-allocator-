package com.disasterrelief.allocator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resource_types")
@Getter
@Setter
@NoArgsConstructor
public class ResourceType extends BaseEntity {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @NotBlank
    @Size(max = 30)
    @Column(name = "unit_of_measure", nullable = false, length = 30)
    private String unitOfMeasure;

    @Column(nullable = false)
    private boolean perishable;

    @Column(nullable = false)
    private boolean active = true;
}
