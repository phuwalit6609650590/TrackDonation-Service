package com.project.trackdonation.messaging;


import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.trackdonation.messaging.dto.AllocationResultMessage;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SnsTemplate snsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.aws.sns.notifications-topic}")
    private String notificationsTopic;

    @Value("${app.aws.sns.admin-alerts-topic}")
    private String adminAlertsTopic;

    public void publishResult(AllocationResultMessage resultMessage, AllocationRequestMessage request) {
        try {
            // 1. ส่งแบบ JSON สำหรับ System-to-System (ดักด้วย unit_name)
            if (request.getRequestingUnit() != null) {
                String jsonPayload = objectMapper.writeValueAsString(resultMessage);
                Map<String, Object> systemHeaders = new HashMap<>();
                systemHeaders.put("unit_name", request.getRequestingUnit());
                
                snsTemplate.convertAndSend(notificationsTopic, jsonPayload, systemHeaders);
                log.info("Sent JSON allocation result to SNS for unit: {}", request.getRequestingUnit());
            }

            // 2. ส่งแบบ Plain Text สำหรับ Human Email (ดักด้วย target_email)
            if (request.getContactEmail() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("📢 Notification: Allocation Request Result\n");
                sb.append("=========================================\n\n");
                sb.append("Request ID: ").append(resultMessage.getReferenceReqId()).append("\n");
                sb.append("Incident ID: ").append(resultMessage.getIncidentId()).append("\n");
                sb.append("Requesting Unit: ").append(request.getRequestingUnit() != null ? request.getRequestingUnit() : "-").append("\n");
                
                sb.append("\nItems Requested:\n");
                sb.append("-----------------------------------------\n");
                
                if (resultMessage.getResults() != null) {
                    for (var item : resultMessage.getResults()) {
                        sb.append("📦 ").append(item.getItemName()).append(" [").append(item.getItemCategory()).append("]\n");
                        sb.append("   Quantity: ").append(item.getAllocatedAmount() != null ? item.getAllocatedAmount() : 0).append("\n");
                        sb.append("   Status: ").append(item.getStatus()).append("\n");
                        if (item.getErrorDetails() != null) {
                            sb.append("   ⚠️ Error: ").append(item.getErrorDetails().getErrorMessage()).append("\n");
                        }
                        sb.append("\n");
                    }
                }
                
                sb.append("=========================================\n");
                sb.append("TrackDonation Service");

                String textPayload = sb.toString();
                Map<String, Object> humanHeaders = new HashMap<>();
                humanHeaders.put("target_email", request.getContactEmail());
                
                snsTemplate.convertAndSend(notificationsTopic, textPayload, humanHeaders);
                log.info("Sent TEXT allocation result to SNS for email: {}", request.getContactEmail());
            }

            // Fallback: ถ้าไม่มีทั้งคู่เลย (ป้องกันเคสแปลกๆ) ให้ส่ง Text คืนไปแบบไม่มี Header
            if (request.getRequestingUnit() == null && request.getContactEmail() == null) {
                log.warn("No requestingUnit or contactEmail found. Skipping SNS notification for request {}", resultMessage.getReferenceReqId());
            }

        } catch (Exception e) {
            log.error("Failed to send SNS message for request {}", resultMessage.getReferenceReqId(), e);
        }
    }

    public void publishTransportSystemDownAlert(String referenceReqId, String incidentId) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🚨 CRITICAL SYSTEM ALERT 🚨\n");
            sb.append("=========================================\n\n");
            sb.append("Alert Type: TRANSPORT_API_DOWN\n");
            sb.append("Request ID: ").append(referenceReqId).append("\n");
            sb.append("Incident ID: ").append(incidentId).append("\n\n");
            sb.append("Message:\n");
            sb.append("The Transport API has failed 3 consecutive times.\n");
            sb.append("The dispatch queue is paused for 1 hour for this request to prevent further failures.\n\n");
            sb.append("Action Required:\n");
            sb.append("- Please check the Resource Service availability.\n");
            sb.append("- You may cancel the request manually if needed.\n\n");
            sb.append("=========================================\n");
            sb.append("TrackDonation Admin System");

            String messagePayload = sb.toString();
            
            Map<String, Object> headers = new HashMap<>();
            headers.put("alert_type", "SYSTEM_DOWN");

            snsTemplate.convertAndSend(adminAlertsTopic, messagePayload, headers);
            log.info("Sent TRANSPORT_API_DOWN alert to SNS topic for request {}", referenceReqId);
        } catch (Exception e) {
            log.error("Failed to send TRANSPORT_API_DOWN alert for request {}", referenceReqId, e);
        }
    }

    public void publishDataIntegrityAlert(String referenceReqId, String incidentId) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ DATA INTEGRITY WARNING ⚠️\n");
            sb.append("=========================================\n\n");
            sb.append("Alert Type: DATA_INTEGRITY_ERROR\n");
            sb.append("Request ID: ").append(referenceReqId).append("\n");
            sb.append("Incident ID: ").append(incidentId).append("\n\n");
            sb.append("Message:\n");
            sb.append("An allocation request failed due to missing required data.\n");
            sb.append("This is usually caused by missing coordinates or warehouse mapping.\n\n");
            sb.append("Action Taken:\n");
            sb.append("The system has automatically cancelled the request and refunded the items to the inventory.\n\n");
            sb.append("=========================================\n");
            sb.append("TrackDonation Admin System");

            String messagePayload = sb.toString();
            
            Map<String, Object> headers = new HashMap<>();
            headers.put("alert_type", "DATA_ERROR");

            snsTemplate.convertAndSend(adminAlertsTopic, messagePayload, headers);
            log.info("Sent DATA_INTEGRITY_ERROR alert to SNS for request {}", referenceReqId);
        } catch (Exception e) {
            log.error("Failed to send DATA_INTEGRITY_ERROR alert for request {}", referenceReqId, e);
        }
    }
}
