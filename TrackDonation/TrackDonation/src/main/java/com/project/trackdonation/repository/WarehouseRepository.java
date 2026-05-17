package com.project.trackdonation.repository;

import com.project.trackdonation.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, String> {
    boolean existsByIncidentId(String incidentId);
    java.util.List<Warehouse> findByIncidentId(String incidentId);
}
