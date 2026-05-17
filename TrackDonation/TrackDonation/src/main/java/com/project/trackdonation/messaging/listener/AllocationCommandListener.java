package com.project.trackdonation.messaging.listener;

import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.entity.AllocationStatus;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.messaging.dto.AllocationResultMessage;
import com.project.trackdonation.messaging.NotificationService;
import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.DispatchOutboxService;
import com.project.trackdonation.repository.AllocationRecordRepository;
import com.project.trackdonation.messaging.TimelineEventPublisher;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class AllocationCommandListener {

    private final DonationService donationService;
    private final NotificationService notificationService;
    private final DispatchOutboxService dispatchOutboxService;
    private final AllocationRecordRepository allocationRecordRepository;
    private final TimelineEventPublisher timelineEventPublisher;

    @SqsListener("${app.aws.sqs.allocation-commands}")
    public void handleAllocationRequest(
            AllocationRequestMessage request,
            @Header("id") String messageId) {

        log.info("[Allocation Flow] รับคำขอเบิกสิ่งของแล้ว (MessageID: {}, Incident: {})", messageId, request.getIncidentId());
        
        long existingQueueCount = 0;
        if (Boolean.TRUE.equals(request.getIsSelfPickup())) {
            log.info("[Allocation Flow] รูปแบบการรับของ: มารับของเอง (Self Pickup)");
        } else {
            log.info("[Allocation Flow] รูปแบบการรับของ: ขอรถขนส่ง (Request Transport)");
            existingQueueCount = allocationRecordRepository.countByStatus(AllocationStatus.WAITING_FOR_TRANSPORT);
        }

        try {
            List<AllocationRecord> records = donationService.allocateItems(request, messageId);

            List<AllocationResultMessage.ItemResult> itemResults = new ArrayList<>();

            for (AllocationRecord record : records) {
                AllocationResultMessage.ItemResult.ItemResultBuilder itemResultBuilder = AllocationResultMessage.ItemResult
                        .builder()
                        .itemCategory(record.getItemCategory() != null ? record.getItemCategory().name() : null)
                        .itemName(record.getItemName())
                        .status(record.getStatus().name());

                boolean isSuccess = record.getStatus() == AllocationStatus.WAITING_FOR_TRANSPORT
                        || record.getStatus() == AllocationStatus.SELF_PICKUP;

                if (isSuccess) {
                    itemResultBuilder
                            .transactionId(record.getTransactionId())
                            .allocatedAmount(record.getAllocatedAmount());
                    log.info("Allocation successful for item: {} status: {} (Transaction: {})",
                            record.getItemName(), record.getStatus().name(), record.getTransactionId());
                } else {
                    itemResultBuilder.errorDetails(AllocationResultMessage.ErrorDetails.builder()
                            .errorCode("ALLOCATION_FAILED")
                            .errorMessage("Failed to allocate item '" + record.getItemName()
                                    + "'. It may be out of stock or frozen.")
                            .build());
                    log.warn("[Allocation Flow] ปฏิเสธการเบิกของสำหรับไอเทม: {} (ของอาจหมดหรือโดนระงับ)", record.getItemName());
                }
                itemResults.add(itemResultBuilder.build());
            }

            AllocationResultMessage resultMessage = AllocationResultMessage.builder()
                    .incidentId(request.getIncidentId())
                    .referenceReqId(request.getReferenceReqId())
                    .results(itemResults)
                    .build();

            notificationService.publishResult(resultMessage, request);

            // Fast-Track Delivery Logic
            if (!Boolean.TRUE.equals(request.getIsSelfPickup())) {
                boolean hasWaitingRecords = records.stream().anyMatch(r -> r.getStatus() == AllocationStatus.WAITING_FOR_TRANSPORT);
                if (hasWaitingRecords) {
                    if (existingQueueCount > 0) {
                        log.info("[Allocation Flow] มีคิวรออยู่ก่อนหน้านี้แล้ว ระบบได้นำคำขอของคุณไปต่อคิว (คุณได้คิวที่ประมาณ {} นับจากที่มีอยู่)", existingQueueCount + 1);
                    } else {
                        log.info("[Allocation Flow] ไม่มีคิวรอรถในระบบ ส่งคำขอเรียกรถทันที (Fast-Track Delivery)");
                        dispatchOutboxService.processPendingAllocations();
                    }
                }
            } else {
                // Self Pickup Timeline Logic
                List<AllocationRecord> selfPickupRecords = records.stream()
                        .filter(r -> r.getStatus() == AllocationStatus.SELF_PICKUP)
                        .toList();
                        
                if (!selfPickupRecords.isEmpty()) {
                    log.info("[Allocation Flow] อนุมัติคำขอแบบ Self Pickup เสร็จสิ้น กำลังส่งข้อมูลไปยัง Timeline");
                    
                    int totalItems = selfPickupRecords.stream().mapToInt(AllocationRecord::getAllocatedAmount).sum();
                    
                    java.util.Map<String, Object> details = new java.util.HashMap<>();
                    details.put("totalItemsAllocated", totalItems);
                    
                    List<java.util.Map<String, Object>> allocatedList = selfPickupRecords.stream().map(r -> {
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("itemCategory", r.getItemCategory() != null ? r.getItemCategory().name() : null);
                        item.put("itemName", r.getItemName());
                        item.put("allocatedAmount", r.getAllocatedAmount());
                        item.put("transactionId", r.getTransactionId());
                        item.put("status", "ALLOCATED");
                        return item;
                    }).toList();
                    details.put("status", "ALLOCATED");
                    details.put("allocatedList", allocatedList);
                    
                    timelineEventPublisher.publishAllocationSuccess(
                            request.getIncidentId(),
                            request.getReferenceReqId(),
                            "อนุมัติการเบิกสิ่งของสำเร็จ (มารับด้วยตนเอง)",
                            "จำนวนสิ่งของที่เบิกทั้งหมด " + totalItems + " ชิ้น กรุณามารับสิ่งของตามที่ระบุ",
                            details);
                }
            }

        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency conflict detected for Message ID {}. Assuming already processed. Error: {}",
                    messageId, e.getMessage());
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("[Allocation Flow] ธุรกิจล็อกจิกปฏิเสธคำขอ: {} (ไม่มีการส่ง Notification กลับ)", e.getMessage());
        } catch (Exception e) {
            log.error("Critical error occurred during allocation: {}", e.getMessage());
            throw e;
        }
    }
}