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
@Table(name = "donation_receipts")
public class DonationReceipt {

    @Id
    @Column(name = "donation_id", length = 50)
    private String donationId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "donor_name", nullable = false)
    private String donorName;

    @Column(name = "storage_location", nullable = false)
    private String storageLocation;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String items;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;
}