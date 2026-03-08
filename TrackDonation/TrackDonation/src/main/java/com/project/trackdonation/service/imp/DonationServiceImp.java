package com.project.trackdonation.service.imp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.entity.DonationReceipt;
import com.project.trackdonation.entity.DonationStatus;
import com.project.trackdonation.entity.InventoryState;
import com.project.trackdonation.entity.InventoryStatus;
import com.project.trackdonation.repository.DonationReceiptRepository;
import com.project.trackdonation.repository.InventoryStateRepository;
import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonationServiceImp implements DonationService {

    private final DonationReceiptRepository receiptRepository;
    private final InventoryStateRepository inventoryRepository;
    private final ObjectMapper objectMapper; //Object เป็น JSON String

    @Override
    @Transactional
    public DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req) {

        String generatedDonationId = "DON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String itemsJson;

        try {
            itemsJson = objectMapper.writeValueAsString(req.getItems());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process items ");
        }

        DonationReceipt receipt = new DonationReceipt()
                .setDonationId(generatedDonationId)
                .setIncidentId(req.getIncidentId())
                .setDonorName(req.getDonorName())
                .setStorageLocation(req.getStorageLocation())
                .setItems(itemsJson)
                .setStatus(DonationStatus.RECEIVED)
                .setCreatedAt(LocalDateTime.now())
                .setIdempotencyKey(req.getIdempotencyKey());
        receiptRepository.save(receipt);

        for (DonationServiceSpec.DonationItem item : req.getItems()) {


            Optional<InventoryState> existingInventory = inventoryRepository
                    .findByIncidentIdAndCategoryAndItemName(req.getIncidentId(), item.getCategory(), item.getItemName());
            if (existingInventory.isPresent()) {
                InventoryState inventory = existingInventory.get();
                inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                inventory.setUpdatedAt(LocalDateTime.now());
                inventory.setStatus(InventoryStatus.IN_STOCK);
                inventoryRepository.save(inventory);
            } else {
                InventoryState newInventory = new InventoryState()
                        .setInventoryId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .setIncidentId(req.getIncidentId())
                        .setCategory(item.getCategory())
                        .setItemName(item.getItemName())
                        .setAvailableQty(item.getQuantity())
                        .setStatus(InventoryStatus.IN_STOCK)
                        .setUpdatedAt(LocalDateTime.now());
                inventoryRepository.save(newInventory);
            }
        }

        return new DonationServiceSpec.DonationReceiptInfo()
                .setDonationId(generatedDonationId)
                .setStatus(DonationStatus.RECEIVED.name())
                .setCreatedAt(receipt.getCreatedAt());
    }

    @Override
    public List<DonationServiceSpec.InventoryInfo> getInventoryByIncident(String incidentId) {
        return inventoryRepository.findAllByIncidentId(incidentId).stream()
                .map(this::toInventoryInfo)
                .collect(Collectors.toList());
    }

    private DonationServiceSpec.InventoryInfo toInventoryInfo(InventoryState inventory) {
        return new DonationServiceSpec.InventoryInfo()
                .setIncidentId(inventory.getIncidentId())
                .setCategory(inventory.getCategory())
                .setItemName(inventory.getItemName())
                .setAvailableQty(inventory.getAvailableQty())
                .setUpdatedAt(inventory.getUpdatedAt());
    }
}