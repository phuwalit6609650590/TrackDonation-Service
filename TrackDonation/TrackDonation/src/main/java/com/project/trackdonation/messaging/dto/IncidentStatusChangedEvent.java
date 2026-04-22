package com.project.trackdonation.messaging.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IncidentStatusChangedEvent {
    private String incidentId;
    private String previousStatus;
    private String currentStatus;
    private String incidentType;
    private String severity;
    private LocalDateTime updatedAt;
}
