package com.project.trackdonation.messaging.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IncidentStatusChangedEvent {
    private String incidentId;
    private String previousStatus;
    private String currentStatus;
    private LocationDTO location;
    private LocalDateTime updatedAt;

    @Data
    public static class LocationDTO {
        private String type;
        private double[] coordinates;
    }
}
