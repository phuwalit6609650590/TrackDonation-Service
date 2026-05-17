package com.project.trackdonation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationPublisher {

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.aws.sqs.allocation-commands}")
    private String allocationCommandsQueue;

    public void publishAllocationRequest(AllocationRequestMessage request) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(request);
            sqsTemplate.send(allocationCommandsQueue, jsonPayload);
            log.info("[SQS Publisher] Published allocation request for referenceReqId: {} to queue: {}",
                    request.getReferenceReqId(), allocationCommandsQueue);
        } catch (JsonProcessingException e) {
            log.error("[SQS Publisher] Failed to serialize allocation request: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize allocation request", e);
        } catch (Exception e) {
            log.error("[SQS Publisher] Failed to publish allocation request: {}", e.getMessage());
            throw new RuntimeException("Failed to publish allocation request to SQS", e);
        }
    }
}
