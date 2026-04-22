package com.project.trackdonation.repository;

import com.project.trackdonation.entity.AllocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllocationRecordRepository extends JpaRepository<AllocationRecord, String> {
    List<AllocationRecord> findByIncidentId(String incidentId);
    List<AllocationRecord> findByShelterId(String shelterId);
    List<AllocationRecord> findByIncidentIdAndShelterId(String incidentId, String shelterId);
}