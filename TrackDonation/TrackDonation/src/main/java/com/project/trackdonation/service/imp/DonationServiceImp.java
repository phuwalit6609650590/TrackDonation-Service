package com.project.trackdonation.service.imp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.entity.*;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.repository.AllocationRecordRepository;
import com.project.trackdonation.client.IncidentApiClient;
import com.project.trackdonation.client.ShelterApiClient;
import com.project.trackdonation.repository.DonationReceiptRepository;
import com.project.trackdonation.repository.InventoryStateRepository;
import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

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
    private final ShelterApiClient shelterApiClient;
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
                try {
                    inventoryRepository.save(newInventory);
                } catch (DataIntegrityViolationException e) {
                    InventoryState inventory = inventoryRepository
                            .findByIncidentIdAndCategoryAndItemName(req.getIncidentId(), item.getCategory(),
                                    item.getItemName())
                            .orElseThrow(() -> new RuntimeException("Concurrent inventory update failed"));
                    inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                    inventory.setUpdatedAt(LocalDateTime.now());
                    inventory.setStatus(InventoryStatus.IN_STOCK);
                    inventoryRepository.save(inventory);
                }
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
    public List<AllocationRecord> allocateItems(AllocationRequestMessage req, String messageId) {
        
        List<AllocationRecord> results = new java.util.ArrayList<>();
        
        // Fast fail if shelter is invalid for the whole request
        boolean shelterValid = true;
        if (req.getShelterId() != null && !req.getShelterId().trim().isEmpty()
                && !shelterApiClient.verifyShelterExists(req.getShelterId())) {
            shelterValid = false;
        }

        int index = 0;
        for (AllocationRequestMessage.ItemRequest itemReq : req.getItems()) {
            String uniqueMessageId = messageId + "#" + index++;

            AllocationRecord record = new AllocationRecord()
                    .setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .setReferenceReqId(req.getReferenceReqId())
                    .setIncidentId(req.getIncidentId())
                    .setItemCategory(itemReq.getItemCategory())
                    .setItemName(itemReq.getItemName())
                    .setRequestingUnit(req.getRequestingUnit())
                    .setContactEmail(req.getContactEmail() != null ? req.getContactEmail() : "no-reply@example.com")
                    .setShelterId(req.getShelterId())
                    .setMessageId(uniqueMessageId)
                    .setCreatedAt(LocalDateTime.now());

            if (!shelterValid) {
                record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                record.setAllocatedAmount(0);
                results.add(allocationRepository.save(record));
                continue;
            }

            Optional<InventoryState> inventoryOpt = inventoryRepository
                    .findByIncidentIdAndCategoryAndItemName(req.getIncidentId(), itemReq.getItemCategory(), itemReq.getItemName());

            if (inventoryOpt.isEmpty()) {
                record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                record.setAllocatedAmount(0);
            } else {
                InventoryState inventory = inventoryOpt.get();
                
                if (inventory.getStatus() == InventoryStatus.FROZEN) {
                    record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                    record.setAllocatedAmount(0);
                    results.add(allocationRepository.save(record));
                    continue;
                }

                if (inventory.getAvailableQty() >= itemReq.getAmountNeeded()) {
                    inventory.setAvailableQty(inventory.getAvailableQty() - itemReq.getAmountNeeded());
                    inventory.setUpdatedAt(LocalDateTime.now());

                    if (inventory.getAvailableQty() == 0) {
                        inventory.setStatus(InventoryStatus.OUT_OF_STOCK);
                    }

                    inventoryRepository.save(inventory);
                    record.setStatus(com.project.trackdonation.entity.AllocationStatus.SUCCESS);
                    record.setAllocatedAmount(itemReq.getAmountNeeded());

                } else {
                    record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                    record.setAllocatedAmount(0);
                }
            }

            results.add(allocationRepository.save(record));
        }

        return results;
    }

    @Override
    public List<DonationServiceSpec.AllocationInfo> getAllocations(String incidentId, String shelterId) {
        List<AllocationRecord> records;

        if (incidentId != null && !incidentId.trim().isEmpty() && shelterId != null && !shelterId.trim().isEmpty()) {
            records = allocationRepository.findByIncidentIdAndShelterId(incidentId, shelterId);
        } else if (incidentId != null && !incidentId.trim().isEmpty()) {
            records = allocationRepository.findByIncidentId(incidentId);
        } else if (shelterId != null && !shelterId.trim().isEmpty()) {
            records = allocationRepository.findByShelterId(shelterId);
        } else {
            records = allocationRepository.findAll();
        }

        return records.stream()
                .map(record -> new DonationServiceSpec.AllocationInfo()
                        .setTransactionId(record.getTransactionId())
                        .setShelterId(record.getShelterId())
                        .setItemCategory(record.getItemCategory())
                        .setAllocatedAmount(record.getAllocatedAmount())
                        .setStatus(record.getStatus().name()))
                .collect(Collectors.toList());
    }
}