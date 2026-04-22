package com.project.trackdonation.messaging.listener;

import com.project.trackdonation.entity.InventoryState;
import com.project.trackdonation.entity.InventoryStatus;
import com.project.trackdonation.messaging.dto.IncidentStatusChangedEvent;
import com.project.trackdonation.repository.InventoryStateRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentStatusChangedListener {

    private final InventoryStateRepository inventoryRepository;

    @SqsListener("${app.aws.sqs.incident-events}")
    @Transactional
    public void handleIncidentStatusChanged(
            IncidentStatusChangedEvent event,
            @Header("id") String messageId) {
            
        log.info("[SQS Listener] Received Incident Status Changed (MessageID: {}, Incident: {})", messageId, event.getIncidentId());

        if ("CLOSED".equalsIgnoreCase(event.getCurrentStatus()) ||
            "REJECTED".equalsIgnoreCase(event.getCurrentStatus())) {
            List<InventoryState> inventories = inventoryRepository.findAllByIncidentId(event.getIncidentId());
            if (!inventories.isEmpty()) {
                log.info("Locking {} inventory items to FROZEN for Incident {}", inventories.size(), event.getIncidentId());
                for (InventoryState inventory : inventories) {
                    if (inventory.getAvailableQty() > 0) {
                        inventory.setStatus(InventoryStatus.FROZEN);
                        inventoryRepository.save(inventory);
                    }
                }
            } else {
                log.info("No inventory found to lock for Incident {}", event.getIncidentId());
            }
        } else {
            log.info("Ignoring incident status '{}' for Incident {}", event.getCurrentStatus(), event.getIncidentId());
        }
    }
}
