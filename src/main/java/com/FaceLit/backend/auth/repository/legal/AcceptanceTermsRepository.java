package com.FaceLit.backend.auth.repository.legal;

import java.util.UUID;
import java.util.Optional; 

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.FaceLit.backend.auth.model.legal.AcceptanceTerms;
import com.FaceLit.backend.auth.model.security.User;

@Repository
public interface AcceptanceTermsRepository extends JpaRepository<AcceptanceTerms, UUID> {

    boolean existsByUserAndAcceptedTrueAndNoticeVersion(User user, String noticeVersion);

    Optional<AcceptanceTerms> findByUserAndNoticeVersion(User user, String noticeVersion);

}
