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
@Table(name = "allocation_records")
public class AllocationRecord {

    @Id
    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "reference_req_id", nullable = false)
    private String referenceReqId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_category", nullable = false)
    private ItemCategory itemCategory;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "allocated_amount", nullable = false)
    private Integer allocatedAmount;

    @Column(name = "requesting_unit", nullable = false)
    private String requestingUnit;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}