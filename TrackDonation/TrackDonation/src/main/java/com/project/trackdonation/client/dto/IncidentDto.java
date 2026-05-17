package com.project.trackdonation.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class IncidentDto {
    @JsonProperty("incident_id")
    private String incidentId;
    
    private String status;
    private LocationDto location;
    
    @JsonProperty("address_name")
    private String addressName;

    @Data
    public static class LocationDto {
        private String type;
        // coordinates[0] = longitude, coordinates[1] = latitude
        private List<Double> coordinates;
    }
}
