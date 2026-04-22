package com.project.trackdonation.messaging.listener;

import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.entity.AllocationStatus;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.messaging.dto.AllocationResultMessage;
import com.project.trackdonation.messaging.NotificationService;
import com.project.trackdonation.service.DonationService;
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

    @SqsListener("${app.aws.sqs.allocation-commands}")
    public void handleAllocationRequest(
            AllocationRequestMessage request,
            @Header("id") String messageId) {

        log.info("[SQS Listener] Received allocation request (MessageID: {})", messageId);

        try {
            List<AllocationRecord> records = donationService.allocateItems(request, messageId);

            List<AllocationResultMessage.ItemResult> itemResults = new ArrayList<>();

            for (AllocationRecord record : records) {
                AllocationResultMessage.ItemResult.ItemResultBuilder itemResultBuilder = AllocationResultMessage.ItemResult
                        .builder()
                        .itemCategory(record.getItemCategory() != null ? record.getItemCategory().name() : null)
                        .itemName(record.getItemName())
                        .status(record.getStatus().name());

                if (record.getStatus() == AllocationStatus.SUCCESS) {
                    itemResultBuilder
                            .transactionId(record.getTransactionId())
                            .allocatedAmount(record.getAllocatedAmount());
                    log.info("Allocation successful for item: {} (Transaction: {})", record.getItemName(),
                            record.getTransactionId());
                } else {
                    itemResultBuilder.errorDetails(AllocationResultMessage.ErrorDetails.builder()
                            .errorCode("ALLOCATION_FAILED")
                            .errorMessage("Failed to allocate item '" + record.getItemName()
                                    + "'. It may be out of stock, frozen, or shelter is invalid.")
                            .build());
                    log.warn("Allocation rejected for item: {}", record.getItemName());
                }
                itemResults.add(itemResultBuilder.build());
            }

            AllocationResultMessage resultMessage = AllocationResultMessage.builder()
                    .incidentId(request.getIncidentId())
                    .referenceReqId(request.getReferenceReqId())
                    .results(itemResults)
                    .build();

            notificationService.publishResult(resultMessage, request);

        } catch (DataIntegrityViolationException e) {

            log.warn("Idempotency conflict detected for Message ID {}. Assuming already processed. Error: {}",
                    messageId, e.getMessage());
        } catch (Exception e) {
            log.error("Critical error occurred during allocation: {}", e.getMessage());
            throw e;
        }
    }
}