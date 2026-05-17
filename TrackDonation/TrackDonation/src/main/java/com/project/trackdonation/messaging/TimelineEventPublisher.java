package com.project.trackdonation.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineEventPublisher {

    private final SnsTemplate snsTemplate;
    
    @Value("${app.aws.sns.timeline-events}")
    private String timelineTopic;

    public void publishAllocationSuccess(String incidentId, String referenceReqId, String title, String description, Object details) {
        try {
            TimelineEvent event = TimelineEvent.builder()
                    .eventId("EVT-TD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .eventType("DONATION_ALLOCATED")
                    .incidentId(incidentId)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .source("TrackDonationService")
                    .title(title)
                    .description(description)
                    .referenceReqId(referenceReqId)
                    .details(details)
                    .build();

            snsTemplate.convertAndSend(timelineTopic, event);
            log.info("Published TimelineEvent for incident {}: {}", incidentId, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish TimelineEvent for incident {}: {}", incidentId, e.getMessage());
        }
    }

    @Data
    @Builder
    public static class TimelineEvent {
        private String eventId;
        private String eventType;
        private String incidentId;
        private String timestamp;
        private String source;
        private String title;
        private String description;
        private String referenceReqId;
        private Object details;
    }
}
