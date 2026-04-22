package com.project.trackdonation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.trackdonation.messaging.dto.AllocationResultMessage;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import java.util.HashMap;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SnsTemplate snsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.aws.sns.notifications-topic}")
    private String notificationsTopic;

    public void publishResult(AllocationResultMessage resultMessage, AllocationRequestMessage request) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(resultMessage);
            
            Map<String, Object> headers = new HashMap<>();
            if (request.getContactEmail() != null) {
                headers.put("target_email", request.getContactEmail());
            }
            if (request.getRequestingUnit() != null) {
                headers.put("unit_name", request.getRequestingUnit());
            }

            snsTemplate.convertAndSend(notificationsTopic, jsonPayload, headers);
            log.info("Sent allocation result to SNS for request {} with attributes", resultMessage.getReferenceReqId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SNS payload for request {}", resultMessage.getReferenceReqId(), e);
        } catch (Exception e) {
            log.error("Failed to send SNS message for request {}", resultMessage.getReferenceReqId(), e);
        }
    }
}
