package com.project.trackdonation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SnsTemplate snsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.aws.sns.notifications-topic}")
    private String notificationsTopic;

    public void sendAllocationResult(String incidentId, String transactionId, String status, String message) {
        try {
            Map<String, String> payload = Map.of(
                    "incidentId", incidentId,
                    "transactionId", transactionId,
                    "status", status,
                    "message", message);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            snsTemplate.convertAndSend(notificationsTopic, jsonPayload);
            log.info("Sent allocation result to SNS for transaction {}: {}", transactionId, status);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SNS payload for transaction {}", transactionId, e);
        } catch (Exception e) {
            log.error("Failed to send SNS message for transaction {}", transactionId, e);
        }
    }
}
