package com.project.trackdonation.repository;

import com.project.trackdonation.entity.AllocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationRecordRepository extends JpaRepository<AllocationRecord, String> {
}