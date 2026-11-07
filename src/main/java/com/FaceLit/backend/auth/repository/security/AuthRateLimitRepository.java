package com.FaceLit.backend.auth.repository.security;

import java.time.OffsetDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.FaceLit.backend.auth.model.security.AuthRateLimit;

public interface AuthRateLimitRepository extends JpaRepository<AuthRateLimit, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AuthRateLimit b where b.key = :key")
    Optional<AuthRateLimit> lock(@Param("key") String key);
    long deleteByWindowStartBefore(OffsetDateTime before);
}
