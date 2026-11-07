package com.FaceLit.backend.auth.repository.security;

import com.FaceLit.backend.auth.model.security.AccountAccessRequest;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountAccessRequestRepository extends JpaRepository<AccountAccessRequest, UUID> {
    List<AccountAccessRequest> findByUser_IdUserAndUsedFalse(UUID userId);
    Optional<AccountAccessRequest> findByTokenHash(String tokenHash);
    @Query("select r.id from AccountAccessRequest r where r.used = false and r.sendStatus in ('PENDING', 'FAILED') and r.attempts < 3")
    List<UUID> findDeliveryCandidates();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountAccessRequest r where r.id = :id")
    Optional<AccountAccessRequest> lockById(@Param("id") UUID id);
    List<AccountAccessRequest> findByTargetEmailIgnoreCaseAndUsedFalse(String email);
}
