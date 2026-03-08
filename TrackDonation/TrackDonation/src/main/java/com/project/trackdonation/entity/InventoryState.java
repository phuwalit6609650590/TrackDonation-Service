package com.project.trackdonation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Accessors(chain = true)
@Table(name = "inventory_states")
public class InventoryState {

    @Id
    @Column(name = "inventory_id", length = 50)
    private String inventoryId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(nullable = false)
    private String category;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryStatus status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}