package com.project.trackdonation.messaging.listener;

import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.entity.AllocationStatus;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.messaging.dto.AllocationResultMessage;
import com.project.trackdonation.messaging.NotificationService;
import com.project.trackdonation.service.DonationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AllocationCommandListener {

    private final DonationService donationService;
    private final SqsTemplate sqsTemplate;
    private final NotificationService notificationService;

    @SqsListener("donation.allocation.commands.v1")
    public void handleAllocationRequest(
            AllocationRequestMessage request,
            @Header("MessageId") String messageId) {

        log.info("[SQS Listener] Received allocation request (MessageID: {})", messageId);

        try {
            AllocationRecord record = donationService.allocateItem(request, messageId);

            AllocationResultMessage.AllocationResultMessageBuilder resultBuilder = AllocationResultMessage.builder()
                    .referenceReqId(record.getReferenceReqId())
                    .status(record.getStatus().name());

            if (record.getStatus() == AllocationStatus.SUCCESS) {
                resultBuilder.incidentId(record.getIncidentId())
                        .transactionId(record.getTransactionId())
                        .itemCategory(record.getItemCategory().name())
                        .itemName(record.getItemName())
                        .allocatedAmount(record.getAllocatedAmount());
                log.info("Allocation successful! (Transaction: {})", record.getTransactionId());
            } else {
                resultBuilder.errorDetails(AllocationResultMessage.ErrorDetails.builder()
                        .errorCode("OUT_OF_STOCK")
                        .errorMessage("The requested item '" + request.getItemName() + "' has insufficient inventory.")
                        .build());
                log.warn("Allocation rejected! (Reason: Insufficient inventory)");
            }

            AllocationResultMessage resultMessage = resultBuilder.build();

            sqsTemplate.send(to -> to
                    .queue("evac.allocation.results.v1")
                    .payload(resultMessage)
                    .header("correlationId", messageId));
            log.info("Result sent back to queue evac.allocation.results.v1 successfully\n");

            // --- SNS NOTIFICATION ---
            if (record.getStatus() == AllocationStatus.SUCCESS) {
                notificationService.sendAllocationResult(
                        record.getIncidentId(),
                        record.getTransactionId(),
                        "SUCCESS",
                        "Successfully allocated " + record.getAllocatedAmount() + " of " + record.getItemName());
            } else {
                notificationService.sendAllocationResult(
                        record.getIncidentId(),
                        record.getTransactionId(),
                        "FAILED",
                        "Failed to allocate " + request.getItemName() + " (Insufficient or Out of Stock)");
            }

        } catch (DataIntegrityViolationException e) {
            
            log.warn("Idempotency conflict detected for Message ID {}. Assuming already processed. Error: {}",
                    messageId, e.getMessage());
        } catch (Exception e) {
            log.error("Critical error occurred during allocation: {}", e.getMessage());
            throw e;
        }
    }
}