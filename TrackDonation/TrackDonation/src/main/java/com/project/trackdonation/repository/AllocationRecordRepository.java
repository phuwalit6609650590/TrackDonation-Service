package com.project.trackdonation.repository;

import com.project.trackdonation.entity.AllocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AllocationRecordRepository extends JpaRepository<AllocationRecord, String> {
    List<AllocationRecord> findByIncidentId(String incidentId);
    List<AllocationRecord> findByReferenceReqId(String referenceReqId);
    List<AllocationRecord> findByStatus(com.project.trackdonation.entity.AllocationStatus status);
    long countByStatus(com.project.trackdonation.entity.AllocationStatus status);
    List<AllocationRecord> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            com.project.trackdonation.entity.AllocationStatus status, 
            java.time.LocalDateTime time);
    List<AllocationRecord> findByStatusOrderByCreatedAtAsc(com.project.trackdonation.entity.AllocationStatus status);

    @Query("SELECT a FROM AllocationRecord a WHERE " +
           "(:incidentId IS NULL OR :incidentId = '' OR a.incidentId = :incidentId) AND " +
           "(:query IS NULL OR :query = '' OR LOWER(a.requestingUnit) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(a.contactEmail) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<AllocationRecord> searchHistory(@Param("query") String query, @Param("incidentId") String incidentId, Pageable pageable);

    long countByStatusAndCreatedAtBefore(com.project.trackdonation.entity.AllocationStatus status, java.time.LocalDateTime createdAt);
}