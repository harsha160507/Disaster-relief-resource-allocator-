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
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;

    @Size(max = 80)
    @Column(length = 80)
    private String code;

    @Size(max = 255)
    @Column(length = 255)
    private String contactEmail;

    @Column(nullable = false)
    private boolean active = true;
}
