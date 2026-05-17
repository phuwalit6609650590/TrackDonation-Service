package com.project.trackdonation.service;

import com.project.trackdonation.entity.InventoryState;
import com.project.trackdonation.entity.Warehouse;
import com.project.trackdonation.repository.InventoryStateRepository;
import com.project.trackdonation.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryStateRepository inventoryStateRepository;
    private final Random random = new Random();

    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    public List<Warehouse> getWarehousesByIncidentId(String incidentId) {
        return warehouseRepository.findByIncidentId(incidentId);
    }

    public Optional<Warehouse> getWarehouseById(String id) {
        return warehouseRepository.findById(id);
    }

    @Transactional
    public Warehouse createWarehouse(Warehouse warehouse) {
        if (warehouse.getWarehouseId() == null || warehouse.getWarehouseId().isEmpty()) {
            warehouse.setWarehouseId("WH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        warehouse.setCreatedAt(LocalDateTime.now());
        Warehouse saved = warehouseRepository.save(warehouse);
        linkOrphanedInventory(saved.getIncidentId(), saved.getWarehouseId());
        return saved;
    }

    @Transactional
    public Warehouse updateWarehouse(String id, Warehouse updated) {
        return warehouseRepository.findById(id).map(w -> {
            if (updated.getName() != null) {
                w.setName(updated.getName());
            }
            if (updated.getLatitude() != null) {
                w.setLatitude(updated.getLatitude());
            }
            if (updated.getLongitude() != null) {
                w.setLongitude(updated.getLongitude());
            }
            if (updated.getIsActive() != null) {
                w.setIsActive(updated.getIsActive());
            }
            Warehouse saved = warehouseRepository.save(w);
            linkOrphanedInventory(saved.getIncidentId(), saved.getWarehouseId());
            return saved;
        }).orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
    }

    @Transactional
    public void deleteWarehouse(String id) {
        warehouseRepository.deleteById(id);
    }

    private void linkOrphanedInventory(String incidentId, String warehouseId) {
        if (incidentId != null && warehouseId != null) {
            List<InventoryState> orphans = inventoryStateRepository.findAllByIncidentId(incidentId);
            for (InventoryState inv : orphans) {
                if (inv.getWarehouseId() == null || inv.getWarehouseId().isEmpty()) {
                    inv.setWarehouseId(warehouseId);
                    inventoryStateRepository.save(inv);
                }
            }
        }
    }

}
