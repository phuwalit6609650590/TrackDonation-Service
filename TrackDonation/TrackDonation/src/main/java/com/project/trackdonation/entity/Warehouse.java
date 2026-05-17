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
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @Column(name = "warehouse_id", length = 50)
    private String warehouseId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "incident_id")
    private String incidentId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
