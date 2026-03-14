package com.project.trackdonation.service.imp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.entity.*;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.repository.AllocationRecordRepository;
import com.project.trackdonation.client.IncidentApiClient;
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
    private final AllocationRecordRepository allocationRepository;
    private final IncidentApiClient incidentApiClient;
    private final ObjectMapper objectMapper; // Object เป็น JSON String

    @Override
    @Transactional
    public DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req) {

        // Verify Incident exists
        incidentApiClient.verifyIncidentStatus(req.getIncidentId());

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
                    .findByIncidentIdAndCategoryAndItemName(req.getIncidentId(), item.getCategory(),
                            item.getItemName());
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

        // Verify Incident exists
        incidentApiClient.verifyIncidentStatus(incidentId);

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

    @Override
    @Transactional
    public AllocationRecord allocateItem(AllocationRequestMessage req, String messageId) {

        AllocationRecord record = new AllocationRecord()
                .setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .setReferenceReqId(req.getReferenceReqId())
                .setIncidentId(req.getIncidentId())
                .setItemCategory(req.getItemCategory())
                .setItemName(req.getItemName())
                .setRequestingUnit(req.getRequestingUnit())
                .setContactEmail("contact@rescue.com")
                .setCreatedAt(LocalDateTime.now());

        Optional<InventoryState> inventoryOpt = inventoryRepository
                .findByIncidentIdAndCategoryAndItemName(req.getIncidentId(), req.getItemCategory(), req.getItemName());

        if (inventoryOpt.isEmpty()) {
            record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
            record.setAllocatedAmount(0);
        } else {
            InventoryState inventory = inventoryOpt.get();
            if (inventory.getAvailableQty() >= req.getAmountNeeded()) {
                inventory.setAvailableQty(inventory.getAvailableQty() - req.getAmountNeeded());
                inventory.setUpdatedAt(LocalDateTime.now());

                if (inventory.getAvailableQty() == 0) {
                    inventory.setStatus(InventoryStatus.OUT_OF_STOCK);
                }

                inventoryRepository.save(inventory);
                record.setStatus(com.project.trackdonation.entity.AllocationStatus.SUCCESS);
                record.setAllocatedAmount(req.getAmountNeeded());

            } else {
                record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                record.setAllocatedAmount(0);
            }
        }

        return allocationRepository.save(record);
    }

}