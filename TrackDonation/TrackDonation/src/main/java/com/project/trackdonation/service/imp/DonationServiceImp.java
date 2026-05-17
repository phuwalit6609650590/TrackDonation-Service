package com.project.trackdonation.service.imp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.entity.*;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.repository.AllocationRecordRepository;
import com.project.trackdonation.repository.IncidentReferenceRepository;
import com.project.trackdonation.service.IncidentSyncService;
import com.project.trackdonation.messaging.TimelineEventPublisher;

import com.project.trackdonation.repository.DonationReceiptRepository;
import com.project.trackdonation.repository.InventoryStateRepository;
import com.project.trackdonation.repository.WarehouseRepository;
import com.project.trackdonation.exception.IncidentNotFoundException;
import com.project.trackdonation.client.IncidentApiClient;
import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonationServiceImp implements DonationService {

    private final DonationReceiptRepository receiptRepository;
    private final InventoryStateRepository inventoryRepository;
    private final AllocationRecordRepository allocationRepository;
    private final IncidentReferenceRepository incidentReferenceRepository;
    private final WarehouseRepository warehouseRepository;
    private final IncidentApiClient incidentApiClient;
    private final ObjectMapper objectMapper; // Object เป็น JSON String
    private final TimelineEventPublisher timelineEventPublisher;

    private boolean verifyOrFallbackIncidentForDonation(String incidentId) {
        log.info("[Donation Flow] ตรวจสอบ IncidentID: {}", incidentId);
        Optional<IncidentReference> refOpt = incidentReferenceRepository.findById(incidentId);
        if (refOpt.isPresent()) {
            String status = refOpt.get().getStatus();
            log.info("[Donation Flow] พบ IncidentID: {} ในระบบ Local DB (Status: {})", incidentId, status);
            if ("CLOSED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status) || "REPORTED".equalsIgnoreCase(status)) {
                log.warn("[Donation Flow] ปฏิเสธการรับบริจาคเนื่องจาก Incident {} อยู่ในสถานะ {}", incidentId, status);
                throw new IllegalArgumentException("Cannot donate to a CLOSED, REJECTED, or REPORTED incident: " + incidentId);
            }
            if ("PENDING_ASSIGNMENT".equalsIgnoreCase(status)) {
                return true;
            }
            return false; // Valid and active in local DB
        }

        log.info("[Donation Flow] ไม่พบ IncidentID: {} ใน Local DB, กำลังตรวจสอบผ่าน API ภายนอก...", incidentId);
        // Tier 2: API Fallback (Circuit Breaker handled inside verifyIncidentStatus)
        try {
            incidentApiClient.verifyIncidentStatus(incidentId);
            
            // API confirmed it's active. Save it to local DB.
            log.info("[Donation Flow] API ภายนอกยืนยันว่า IncidentID: {} ใช้งานได้ ทำการบันทึกลง Local DB", incidentId);
            IncidentReference newRef = new IncidentReference()
                    .setIncidentId(incidentId)
                    .setStatus("VERIFIED")
                    .setLastUpdatedAt(LocalDateTime.now());
            incidentReferenceRepository.save(newRef);
            return false;
        } catch (Exception ex) {
            log.warn("[Donation Flow] API ภายนอกไม่พบ IncidentID: {} หรือระบบล่ม ({}) รับคำขอเข้าสู่โหมด PENDING_ASSIGNMENT", incidentId, ex.getMessage());
            // Tier 3: Accept into Pending Assignment (Zero-Downtime)
            IncidentReference pendingRef = new IncidentReference()
                    .setIncidentId(incidentId)
                    .setStatus("PENDING_ASSIGNMENT")
                    .setLastUpdatedAt(LocalDateTime.now());
            incidentReferenceRepository.save(pendingRef);
            return true;
        }
    }

    @Override
    @Transactional
    public DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req) {

        log.info("[Donation Flow] รับคำขอบริจาคใหม่จากผู้บริจาค: {}", req.getDonorName());

        String finalIncidentId = req.getIncidentId();
        String finalWarehouseId = req.getWarehouseId();

        // Verify Incident exists with 3-Tier Fallback
        boolean isPending = verifyOrFallbackIncidentForDonation(finalIncidentId);

        if (isPending) {
            if (finalWarehouseId != null && !finalWarehouseId.trim().isEmpty()) {
                log.info("[Donation Flow] ตรวจสอบ WarehouseID: {} สำหรับโหมด PENDING", finalWarehouseId);
                Optional<Warehouse> wOpt = warehouseRepository.findById(finalWarehouseId);
                if (wOpt.isPresent()) {
                    finalIncidentId = wOpt.get().getIncidentId();
                    log.info("[Donation Flow] พบ WarehouseID: {} ในระบบ ทำการเปลี่ยน IncidentID ของคำขอนี้เป็น {}", finalWarehouseId, finalIncidentId);
                } else {
                    log.warn("[Donation Flow] ไม่พบ WarehouseID: {} ในระบบ บันทึกคำขอบริจาคโดยให้โกดังเป็นค่าว่าง (null)", finalWarehouseId);
                    finalWarehouseId = null;
                }
            } else {
                log.info("[Donation Flow] ไม่มีการระบุ WarehouseID เข้ามา บันทึกคำขอบริจาคโดยให้โกดังเป็นค่าว่าง (null)");
                finalWarehouseId = null;
            }
        } else {
            // Incident exists (Active). Check if Warehouse exists.
            log.info("[Donation Flow] ตรวจสอบ WarehouseID: {} สำหรับ Active Incident", finalWarehouseId);
            if (finalWarehouseId == null || warehouseRepository.findById(finalWarehouseId).isEmpty()) {
                log.error("[Donation Flow] ไม่พบ WarehouseID: {} หรือไม่ได้ระบุ ปฏิเสธคำขอบริจาค", finalWarehouseId);
                throw new IllegalArgumentException("Invalid or missing warehouseId: " + finalWarehouseId);
            }
            log.info("[Donation Flow] พบ WarehouseID: {} ในระบบ ดำเนินการรับของเข้าคลัง", finalWarehouseId);
        }

        String generatedDonationId = "DON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String itemsJson;

        try {
            itemsJson = objectMapper.writeValueAsString(req.getItems());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process items ");
        }

        DonationReceipt receipt = new DonationReceipt()
                .setDonationId(generatedDonationId)
                .setIncidentId(finalIncidentId)
                .setDonorName(req.getDonorName())
                .setWarehouseId(finalWarehouseId)
                .setItems(itemsJson)
                .setStatus(DonationStatus.RECEIVED)
                .setCreatedAt(LocalDateTime.now())
                .setIdempotencyKey(req.getIdempotencyKey());
        receiptRepository.save(receipt);

        for (DonationServiceSpec.DonationItem item : req.getItems()) {

            Optional<InventoryState> existingInventory = Optional.empty();
            if (finalWarehouseId != null) {
                existingInventory = inventoryRepository
                        .findByWarehouseIdAndIncidentIdAndCategoryAndItemName(finalWarehouseId, finalIncidentId, item.getCategory(),
                                item.getItemName());
            } else {
                // PENDING mode: ค้นหาสต็อกจาก incidentId+category+itemName แทน (ไม่มี warehouseId)
                existingInventory = inventoryRepository
                        .findFirstByIncidentIdAndCategoryAndItemName(finalIncidentId, item.getCategory(), item.getItemName());
            }

            if (existingInventory.isPresent()) {
                InventoryState inventory = existingInventory.get();
                inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                inventory.setUpdatedAt(LocalDateTime.now());
                inventory.setStatus(InventoryStatus.IN_STOCK);
                inventoryRepository.save(inventory);
            } else {
                InventoryState newInventory = new InventoryState()
                        .setInventoryId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .setWarehouseId(finalWarehouseId)
                        .setIncidentId(finalIncidentId)
                        .setCategory(item.getCategory())
                        .setItemName(item.getItemName())
                        .setAvailableQty(item.getQuantity())
                        .setStatus(InventoryStatus.IN_STOCK)
                        .setUpdatedAt(LocalDateTime.now());
                try {
                    inventoryRepository.save(newInventory);
                } catch (DataIntegrityViolationException e) {
                    if (finalWarehouseId != null) {
                        InventoryState inventory = inventoryRepository
                                .findByWarehouseIdAndIncidentIdAndCategoryAndItemName(finalWarehouseId, finalIncidentId, item.getCategory(),
                                        item.getItemName())
                                .orElseThrow(() -> new RuntimeException("Concurrent inventory update failed"));
                        inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                        inventory.setUpdatedAt(LocalDateTime.now());
                        inventory.setStatus(InventoryStatus.IN_STOCK);
                        inventoryRepository.save(inventory);
                    } else {
                        throw new RuntimeException("Concurrent inventory update failed for PENDING");
                    }
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
        if (!incidentReferenceRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException("Incident not found: " + incidentId);
        }

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
        
        Optional<IncidentReference> refOpt = incidentReferenceRepository.findById(req.getIncidentId());
        if (refOpt.isPresent() && "PENDING_ASSIGNMENT".equalsIgnoreCase(refOpt.get().getStatus())) {
            log.error("[Allocation Flow] ปฏิเสธการเบิกของเนื่องจาก Incident {} อยู่ในสถานะ PENDING_ASSIGNMENT", req.getIncidentId());
            throw new IllegalStateException("Cannot allocate items for a PENDING_ASSIGNMENT incident: " + req.getIncidentId());
        }

        List<AllocationRecord> results = new java.util.ArrayList<>();

        String processedReqId = req.getReferenceReqId();
        if (processedReqId == null || processedReqId.trim().isEmpty()) {
            processedReqId = "REQ-TD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        // Validation Phase (All-or-Nothing Check)
        boolean canAllocateAll = true;
        String failReason = "";
        for (AllocationRequestMessage.ItemRequest itemReq : req.getItems()) {
            Optional<InventoryState> inventoryOpt = inventoryRepository
                    .findByWarehouseIdAndIncidentIdAndCategoryAndItemName(req.getWarehouseId(), req.getIncidentId(), itemReq.getItemCategory(), itemReq.getItemName());
            
            if (inventoryOpt.isEmpty()) {
                log.warn("[Allocation Flow] ปฏิเสธการเบิก: ไม่พบของ {} ในคลัง", itemReq.getItemName());
                canAllocateAll = false;
                failReason = "Item not found in warehouse: " + itemReq.getItemName();
                break;
            }
            
            InventoryState inventory = inventoryOpt.get();
            if (inventory.getStatus() == InventoryStatus.FROZEN) {
                log.warn("[Allocation Flow] ปฏิเสธการเบิก: ของ {} ถูกแช่แข็ง (FROZEN)", itemReq.getItemName());
                canAllocateAll = false;
                failReason = "Item is frozen: " + itemReq.getItemName();
                break;
            }
            
            if (inventory.getAvailableQty() < itemReq.getAmountNeeded()) {
                log.warn("[Allocation Flow] ปฏิเสธการเบิก: ของ {} มีไม่พอ (ต้องการ {}, มี {})", itemReq.getItemName(), itemReq.getAmountNeeded(), inventory.getAvailableQty());
                canAllocateAll = false;
                failReason = "Insufficient inventory for item: " + itemReq.getItemName() + " (Requested: " + itemReq.getAmountNeeded() + ", Available: " + inventory.getAvailableQty() + ")";
                break;
            }
        }

        int index = 0;
        for (AllocationRequestMessage.ItemRequest itemReq : req.getItems()) {
            String uniqueMessageId = messageId + "#" + index++;

            AllocationRecord record = new AllocationRecord()
                    .setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .setReferenceReqId(processedReqId)
                    .setIncidentId(req.getIncidentId())
                    .setItemCategory(itemReq.getItemCategory())
                    .setItemName(itemReq.getItemName())
                    .setRequestingUnit(req.getRequestingUnit())
                    .setContactEmail(req.getContactEmail() != null ? req.getContactEmail() : "no-reply@example.com")
                    .setDestinationLat(req.getDestinationLat())
                    .setDestinationLong(req.getDestinationLong())
                    .setMessageId(uniqueMessageId)
                    .setRetryCount(0)
                    .setNextRetryAt(LocalDateTime.now())
                    .setCreatedAt(LocalDateTime.now());



            if (canAllocateAll) {
                InventoryState inventory = inventoryRepository
                        .findByWarehouseIdAndIncidentIdAndCategoryAndItemName(req.getWarehouseId(), req.getIncidentId(), itemReq.getItemCategory(), itemReq.getItemName())
                        .orElseThrow(() -> new IllegalStateException("Item not found during allocation"));

                inventory.setAvailableQty(inventory.getAvailableQty() - itemReq.getAmountNeeded());
                inventory.setUpdatedAt(LocalDateTime.now());

                if (inventory.getAvailableQty() == 0) {
                    inventory.setStatus(InventoryStatus.OUT_OF_STOCK);
                }

                inventoryRepository.save(inventory);
                
                if (Boolean.TRUE.equals(req.getIsSelfPickup())) {
                    record.setStatus(com.project.trackdonation.entity.AllocationStatus.SELF_PICKUP);
                } else {
                    record.setStatus(com.project.trackdonation.entity.AllocationStatus.WAITING_FOR_TRANSPORT);
                }
                
                record.setAllocatedAmount(itemReq.getAmountNeeded());
                record.setWarehouseId(req.getWarehouseId());
            } else {
                record.setStatus(com.project.trackdonation.entity.AllocationStatus.FAILED);
                record.setAllocatedAmount(0);
                log.info("[Allocation Flow] {} ถูกบันทึกเป็น FAILED เนื่องจากกฎ All-or-Nothing (สาเหตุ: {})", itemReq.getItemName(), failReason);
            }

            results.add(allocationRepository.save(record));
        }

        return results;
    }

    @Override
    public List<DonationServiceSpec.AllocationInfo> getAllocations(String incidentId) {
        List<AllocationRecord> records;

        if (incidentId != null && !incidentId.trim().isEmpty()) {
            records = allocationRepository.findByIncidentId(incidentId);
        } else {
            records = allocationRepository.findAll();
        }

        return records.stream()
                .map(record -> new DonationServiceSpec.AllocationInfo()
                        .setTransactionId(record.getTransactionId())
                        .setItemCategory(record.getItemCategory())
                        .setAllocatedAmount(record.getAllocatedAmount())
                        .setStatus(record.getStatus().name()))
                .collect(Collectors.toList());
    }

    @Override
    public org.springframework.data.domain.Page<DonationServiceSpec.AllocationHistoryInfo> getAllocationHistory(String query, String incidentId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<AllocationRecord> records = allocationRepository.searchHistory(query, incidentId, pageable);
        
        return records.map(record -> {
            DonationServiceSpec.AllocationHistoryInfo info = new DonationServiceSpec.AllocationHistoryInfo()
                    .setReferenceReqId(record.getReferenceReqId())
                    .setIncidentId(record.getIncidentId())
                    .setItemCategory(record.getItemCategory())
                    .setItemName(record.getItemName())
                    .setAllocatedAmount(record.getAllocatedAmount())
                    .setStatus(record.getStatus().name())
                    .setDestinationLat(record.getDestinationLat() != null ? record.getDestinationLat().toString() : null)
                    .setDestinationLong(record.getDestinationLong() != null ? record.getDestinationLong().toString() : null)
                    .setWarehouseId(record.getWarehouseId())
                    .setCreatedAt(record.getCreatedAt());
            
            if (record.getStatus() == AllocationStatus.WAITING_FOR_TRANSPORT) {
                // Calculate queue position: count records with same status but created before this one
                long olderRecords = allocationRepository.countByStatusAndCreatedAtBefore(AllocationStatus.WAITING_FOR_TRANSPORT, record.getCreatedAt());
                info.setQueuePosition(olderRecords + 1); // 1-indexed queue position
            }
            
            return info;
        });
    }

    @Override
    @Transactional
    public void cancelAndRefundAllocation(String referenceReqId) {
        log.info("[Donation Flow] กำลังยกเลิกคำขอ Allocation และคืนของเข้าสต็อก (ReqID: {})", referenceReqId);
        
        List<AllocationRecord> records = allocationRepository.findAll().stream()
                .filter(r -> referenceReqId.equals(r.getReferenceReqId()))
                .collect(Collectors.toList());

        if (records.isEmpty()) {
            throw new IllegalArgumentException("Allocation request not found: " + referenceReqId);
        }

        boolean isSelfPickup = false;
        String incidentId = records.get(0).getIncidentId();

        for (AllocationRecord record : records) {
            AllocationStatus status = record.getStatus();
            
            if (status == AllocationStatus.SELF_PICKUP) {
                isSelfPickup = true;
            }

            if (status == AllocationStatus.WAITING_FOR_TRANSPORT || 
                status == AllocationStatus.DATA_INTEGRITY_ERROR || 
                status == AllocationStatus.SELF_PICKUP) {
                
                String warehouseId = record.getWarehouseId();
                if (warehouseId == null) {
                    log.warn("[Donation Flow] ไม่สามารถคืนสต็อกได้สำหรับ Item: {} เนื่องจากไม่มี warehouseId ใน Record", record.getItemName());
                } else {
                    Optional<InventoryState> invOpt = inventoryRepository.findByWarehouseIdAndIncidentIdAndCategoryAndItemName(
                            warehouseId, incidentId, record.getItemCategory(), record.getItemName());
                            
                    if (invOpt.isPresent()) {
                        InventoryState inventory = invOpt.get();
                        inventory.setAvailableQty(inventory.getAvailableQty() + record.getAllocatedAmount());
                        
                        if (inventory.getStatus() == InventoryStatus.OUT_OF_STOCK && inventory.getAvailableQty() > 0) {
                            inventory.setStatus(InventoryStatus.IN_STOCK);
                        }
                        
                        inventory.setUpdatedAt(LocalDateTime.now());
                        inventoryRepository.save(inventory);
                        log.info("[Donation Flow] คืนของเข้าสต็อกสำเร็จ: {} จำนวน {} ชิ้น (Warehouse: {})", 
                                record.getItemName(), record.getAllocatedAmount(), warehouseId);
                    } else {
                        log.warn("[Donation Flow] ไม่พบ Inventory เดิมเพื่อคืนสต็อก (Item: {}, Warehouse: {})", 
                                record.getItemName(), warehouseId);
                    }
                }

                // Update record status to CANCELLED
                record.setStatus(AllocationStatus.CANCELLED);
                allocationRepository.save(record);
            } else {
                log.warn("[Donation Flow] ข้ามการคืนสต็อกสำหรับ Item: {} เนื่องจากมีสถานะ {}", record.getItemName(), status);
                if (status == AllocationStatus.DISPATCHED) {
                    throw new IllegalStateException("ไม่สามารถยกเลิกคำขอที่กำลังจัดส่ง (DISPATCHED) ได้");
                }
            }
        }

        if (isSelfPickup) {
            log.info("[Donation Flow] ตรวจพบการยกเลิกคำขอแบบ Self Pickup กำลังส่งข้อมูลไปยัง Timeline");
            
            int totalItems = records.stream().mapToInt(AllocationRecord::getAllocatedAmount).sum();
            List<java.util.Map<String, Object>> allocatedList = records.stream().map(r -> {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("itemCategory", r.getItemCategory() != null ? r.getItemCategory().name() : null);
                item.put("itemName", r.getItemName());
                item.put("allocatedAmount", r.getAllocatedAmount());
                item.put("transactionId", r.getTransactionId());
                item.put("status", "CANCELLED");
                return item;
            }).toList();

            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("action", "CANCELLED");
            details.put("reason", "Admin Cancelled or System Error");
            details.put("status", "CANCELLED");
            details.put("totalItemsAllocated", totalItems);
            details.put("allocatedList", allocatedList);

            timelineEventPublisher.publishAllocationSuccess(
                    incidentId,
                    referenceReqId,
                    "ยกเลิกการเบิกสิ่งของ (มารับของด้วยตนเอง)",
                    "คำขอนี้ถูกยกเลิกและระบบได้คืนสิ่งของทั้งหมดกลับเข้าคลังเรียบร้อยแล้ว",
                    details);
        }
    }

    @Override
    @Transactional
    public void dispatchSelfPickup(String referenceReqId) {
        List<AllocationRecord> records = allocationRepository.findByReferenceReqId(referenceReqId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("ไม่พบคำขอเบิกนี้ในระบบ (Reference ID: " + referenceReqId + ")");
        }

        boolean isSelfPickup = false;
        String incidentId = records.get(0).getIncidentId();

        for (AllocationRecord record : records) {
            if (record.getStatus() != AllocationStatus.SELF_PICKUP) {
                throw new IllegalStateException("เฉพาะคำขอแบบรับของเอง (SELF_PICKUP) เท่านั้นที่กดส่งมอบได้ สถานะปัจจุบัน: " + record.getStatus());
            }
            record.setStatus(AllocationStatus.DISPATCHED);
            allocationRepository.save(record);
            isSelfPickup = true;
        }

        if (isSelfPickup) {
            log.info("[Donation Flow] Mark Self Pickup as DISPATCHED for {}", referenceReqId);
        }
    }
}