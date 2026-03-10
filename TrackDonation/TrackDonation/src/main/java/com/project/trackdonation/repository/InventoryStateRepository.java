package com.project.trackdonation.repository;

import com.project.trackdonation.entity.InventoryState;
import com.project.trackdonation.entity.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InventoryStateRepository extends JpaRepository<InventoryState, String> {
    Optional<InventoryState> findByIncidentIdAndCategoryAndItemName(
            String incidentId,
            ItemCategory category,
            String itemName);

    List<InventoryState> findAllByIncidentId(String incidentId);
}