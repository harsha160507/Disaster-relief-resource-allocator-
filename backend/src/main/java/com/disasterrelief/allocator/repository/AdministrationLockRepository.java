package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.AdministrationLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface AdministrationLockRepository extends JpaRepository<AdministrationLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from AdministrationLock guard where guard.id = 1")
    Optional<AdministrationLock> lockAdministration();
}
