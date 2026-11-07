package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.ChangeHistory;

public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, UUID> {
    List<ChangeHistory> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, UUID entityId);
}