package com.project.trackdonation.messaging.listener;

import com.project.trackdonation.entity.IncidentReference;
import com.project.trackdonation.entity.Warehouse;
import com.project.trackdonation.repository.IncidentReferenceRepository;
import com.project.trackdonation.repository.WarehouseRepository;
import com.project.trackdonation.messaging.dto.IncidentStatusChangedEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentStatusChangedListener {

    private final IncidentReferenceRepository incidentReferenceRepository;
    private final WarehouseRepository warehouseRepository;

    @SqsListener("${app.aws.sqs.incident-events}")
    @Transactional
    public void handleIncidentStatusChanged(
            IncidentStatusChangedEvent event,
            @Header("id") String messageId) {
        log.info("[SQS Listener] Received Incident Status Changed (MessageID: {}, Incident: {})", messageId, event.getIncidentId());

        // 1. Save or Update IncidentReference (EDA Cache)
        IncidentReference ref = incidentReferenceRepository.findById(event.getIncidentId())
                .orElse(new IncidentReference().setIncidentId(event.getIncidentId()));
        
        ref.setStatus(event.getCurrentStatus() != null ? event.getCurrentStatus() : "UNKNOWN");
        ref.setLastUpdatedAt(LocalDateTime.now());
        
        if (event.getLocation() != null && event.getLocation().getCoordinates() != null && event.getLocation().getCoordinates().length >= 2) {
            ref.setLongitude(event.getLocation().getCoordinates()[0]);
            ref.setLatitude(event.getLocation().getCoordinates()[1]);
        }
        
        incidentReferenceRepository.save(ref);
        log.info("Saved/Updated IncidentReference to Local Cache: {} with status {}", event.getIncidentId(), ref.getStatus());

        // 2. Auto-create Warehouse if it doesn't exist
        if (!warehouseRepository.existsByIncidentId(event.getIncidentId())) {
            Warehouse newWh = new Warehouse()
                    .setWarehouseId("WH-" + event.getIncidentId() + "-001")
                    .setIncidentId(event.getIncidentId())
                    .setName("main warehouse " + event.getIncidentId())
                    .setIsActive(true)
                    .setCreatedAt(LocalDateTime.now());
            
            if (ref.getLatitude() != null && ref.getLongitude() != null) {
                // Offset by 6km roughly (+0.054 degrees to both for simplicity)
                newWh.setLatitude(ref.getLatitude() + 0.054);
                newWh.setLongitude(ref.getLongitude() + 0.054);
            } else {
                newWh.setLatitude(0.0);
                newWh.setLongitude(0.0);
            }
            warehouseRepository.save(newWh);
            log.info("[Auto-Warehouse] สร้างโกดังอัตโนมัติสำเร็จ: {}", newWh.getWarehouseId());
        }

        // Note: We no longer freeze inventory on CLOSED or REJECTED. 
        // This allows pending allocations to still move items out of the warehouse.
    }

}
