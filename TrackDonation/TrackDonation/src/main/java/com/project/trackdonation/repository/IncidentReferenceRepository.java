package com.project.trackdonation.repository;

import com.project.trackdonation.entity.IncidentReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentReferenceRepository extends JpaRepository<IncidentReference, String> {
}
