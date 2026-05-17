package com.project.trackdonation.service;

import com.project.trackdonation.client.ResourceAllocationClient;
import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.entity.AllocationStatus;
import com.project.trackdonation.entity.Warehouse;
import com.project.trackdonation.repository.AllocationRecordRepository;
import com.project.trackdonation.repository.WarehouseRepository;
import com.project.trackdonation.messaging.NotificationService;
import com.project.trackdonation.messaging.TimelineEventPublisher;
import com.project.trackdonation.service.DonationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DispatchOutboxService {

    private final AllocationRecordRepository allocationRecordRepository;
    private final WarehouseRepository warehouseRepository;
    private final ResourceAllocationClient resourceAllocationClient;
    private final TimelineEventPublisher timelineEventPublisher;
    private final NotificationService notificationService;
    private final DonationService donationService;

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void processPendingAllocations() {
        List<AllocationRecord> pendingRecords = allocationRecordRepository
                .findByStatusOrderByCreatedAtAsc(AllocationStatus.WAITING_FOR_TRANSPORT);
        if (pendingRecords.isEmpty()) {
            return;
        }

        // Group by referenceReqId preserving FIFO order via LinkedHashMap
        Map<String, List<AllocationRecord>> groupedByReq = pendingRecords.stream()
                .collect(Collectors.groupingBy(AllocationRecord::getReferenceReqId,
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<AllocationRecord>> entry : groupedByReq.entrySet()) {
            String referenceReqId = entry.getKey();
            List<AllocationRecord> batch = entry.getValue();

            // Calculate total items
            int totalItems = batch.stream().mapToInt(AllocationRecord::getAllocatedAmount).sum();

            // Grab the first record to get common data (incidentId, destination,
            // warehouseId)
            AllocationRecord first = batch.get(0);

            // [STRICT FIFO] ตรวจสอบว่าคิวที่ 1 ยังติดเวลา Retry อยู่หรือไม่
            if (first.getNextRetryAt() != null && LocalDateTime.now().isBefore(first.getNextRetryAt())) {
                log.info(
                        "[Dispatch Queue] คิวขอรถ (ReqID: {}) ยังไม่ถึงเวลารอบใหม่ (รอถึง {}) -> ระงับการส่งคิวที่เหลือทั้งหมดชั่วคราวเพื่อรักษาระบบ FIFO (Head-of-Line Blocking)",
                        referenceReqId, first.getNextRetryAt());
                break; // หยุดประมวลผลคิวที่เหลือทันที
            }

            Double destLat = first.getDestinationLat();
            Double destLong = first.getDestinationLong();
            String warehouseId = first.getWarehouseId();

            if (warehouseId == null) {
                log.error("[DATA_INTEGRITY] Request {}: warehouseId is null. ยกเลิกคำขอและคืนของเข้าสต็อกอัตโนมัติ",
                        referenceReqId);
                notificationService.publishDataIntegrityAlert(referenceReqId, first.getIncidentId());
                try {
                    donationService.cancelAndRefundAllocation(referenceReqId);
                } catch (Exception e) {
                    log.error("Failed to cancel and refund allocation for request {}", referenceReqId, e);
                }
                continue;
            }

            Optional<Warehouse> warehouseOpt = warehouseRepository.findById(warehouseId);
            if (warehouseOpt.isEmpty()) {
                log.error(
                        "[DATA_INTEGRITY] Request {}: Warehouse {} not found in DB. ยกเลิกคำขอและคืนของเข้าสต็อกอัตโนมัติ",
                        referenceReqId, warehouseId);
                notificationService.publishDataIntegrityAlert(referenceReqId, first.getIncidentId());
                try {
                    donationService.cancelAndRefundAllocation(referenceReqId);
                } catch (Exception e) {
                    log.error("Failed to cancel and refund allocation for request {}", referenceReqId, e);
                }
                continue;
            }
            Warehouse warehouse = warehouseOpt.get();

            if (destLat == null || destLong == null) {
                log.error(
                        "[DATA_INTEGRITY] Request {}: destination lat/long is null. ยกเลิกคำขอและคืนของเข้าสต็อกอัตโนมัติ",
                        referenceReqId);
                notificationService.publishDataIntegrityAlert(referenceReqId, first.getIncidentId());
                try {
                    donationService.cancelAndRefundAllocation(referenceReqId);
                } catch (Exception e) {
                    log.error("Failed to cancel and refund allocation for request {}", referenceReqId, e);
                }
                continue;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("required_resource_type", "SUPPLY_TRUCK");
            payload.put("incident_id", first.getIncidentId());
            payload.put("request_id", referenceReqId);

            // "incident_location" is the final drop-off point (Shelter/Dest)
            Map<String, Object> incidentLocation = new HashMap<>();
            incidentLocation.put("lat", destLat);
            incidentLocation.put("long", destLong);
            payload.put("incident_location", incidentLocation);

            // "destination" is the pickup point (Our Warehouse)
            Map<String, Object> destPayload = new HashMap<>();
            destPayload.put("destination_type", "PICKUP_SUPPLY");
            destPayload.put("destination_id", warehouse.getWarehouseId());

            Map<String, Object> destLocation = new HashMap<>();
            destLocation.put("lat", warehouse.getLatitude());
            destLocation.put("long", warehouse.getLongitude());
            destPayload.put("location", destLocation);

            payload.put("destination", destPayload);

            // Send request
            boolean success = resourceAllocationClient.requestTransport(payload);

            if (success) {
                for (AllocationRecord record : batch) {
                    record.setStatus(AllocationStatus.DISPATCHED);
                    allocationRecordRepository.save(record);
                }
                log.info("[Allocation Flow] ได้รถสำหรับขนส่งแล้ว (Request: {}) ส่งข้อมูลแจ้งเตือนไปยัง Timeline...",
                        referenceReqId);

                // Construct timeline details
                Map<String, Object> details = new HashMap<>();
                details.put("totalItemsAllocated", totalItems);

                List<Map<String, Object>> allocatedList = batch.stream().map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemCategory", r.getItemCategory());
                    item.put("itemName", r.getItemName());
                    item.put("allocatedAmount", r.getAllocatedAmount());
                    item.put("transactionId", r.getTransactionId());
                    item.put("status", "ALLOCATED");
                    return item;
                }).collect(Collectors.toList());
                details.put("status", "ALLOCATED");
                details.put("allocatedList", allocatedList);

                timelineEventPublisher.publishAllocationSuccess(
                        first.getIncidentId(),
                        referenceReqId,
                        "อนุมัติการเบิกสิ่งของและเรียกรถสำเร็จ",
                        "จำนวนสิ่งของที่เบิกทั้งหมด " + totalItems + " ชิ้น และกำลังเดินทางไปจุดหมาย",
                        details);
            } else {
                boolean shouldSendAlert = false;
                LocalDateTime nextRetryTime;

                for (AllocationRecord record : batch) {
                    int newRetryCount = record.getRetryCount() + 1;
                    if (newRetryCount % 3 == 0) {
                        // พังครบ 3 ครั้ง -> พัก 1 ชั่วโมง
                        nextRetryTime = LocalDateTime.now().plusHours(1);
                        shouldSendAlert = true;
                    } else {
                        // พังทั่วไป -> พัก 5 นาที
                        nextRetryTime = LocalDateTime.now().plusMinutes(5);
                    }

                    record.setRetryCount(newRetryCount);
                    record.setNextRetryAt(nextRetryTime);
                    allocationRecordRepository.save(record);
                }

                if (shouldSendAlert) {
                    log.error(
                            "[Dispatch Queue] Request {} ล้มเหลวครบ 3 ครั้ง (สะสมรวม: {} ครั้ง) ระบบระงับคิวนี้ไว้ 1 ชั่วโมง และส่งแจ้งเตือนหา Admin",
                            referenceReqId, batch.get(0).getRetryCount());
                    notificationService.publishTransportSystemDownAlert(referenceReqId, first.getIncidentId());
                } else {
                    log.warn("[Dispatch Queue] Request {} ล้มเหลว ระบบจะพยายามใหม่ในอีก 5 นาที (ครั้งที่ {})",
                            referenceReqId, batch.get(0).getRetryCount());
                }

                // [STRICT FIFO] เมื่อคิวแรกทำงานล้มเหลว ต้องหยุดทำคิวที่เหลือทั้งหมดทันที!
                break;
            }
        }
    }
}
