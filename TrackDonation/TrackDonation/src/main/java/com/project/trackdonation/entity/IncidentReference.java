package com.project.trackdonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Accessors(chain = true)
@Table(name = "incident_references")
public class IncidentReference {

    @Id
    @Column(name = "incident_id", length = 50)
    private String incidentId;

    @Column(name = "status", nullable = false)
    private String status; // e.g. VERIFIED, CLOSED, PENDING_ASSIGNMENT



    @Column(name = "latitude", nullable = true)
    private Double latitude;

    @Column(name = "longitude", nullable = true)
    private Double longitude;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();
}
