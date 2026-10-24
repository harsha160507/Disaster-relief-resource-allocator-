package com.disasterrelief.allocator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "administration_lock")
@Getter
@NoArgsConstructor
public class AdministrationLock {
    @Id
    private Integer id;
    public AdministrationLock(int id) { this.id = id; }
}
