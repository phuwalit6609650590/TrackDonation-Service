package com.project.trackdonation.service.spec;

import com.project.trackdonation.entity.ItemCategory;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
public class DonationServiceSpec {

    @Accessors(chain = true)
    @Data
    public static class RecordDonationRequest {
        private String incidentId;
        private String donorName;
        private String storageLocation;
        private List<DonationItem> items;
        private String idempotencyKey;
    }

    @Accessors(chain = true)
    @Data
    public static class DonationItem {
        private ItemCategory category;
        private String itemName;
        private Integer quantity;
    }

    @Accessors(chain = true)
    @Data
    public static class DonationReceiptInfo {
        private String donationId;
        private String status;
        private LocalDateTime createdAt;
    }

    @Accessors(chain = true)
    @Data
    public static class InventoryInfo {
        private String incidentId;
        private ItemCategory category;
        private String itemName;
        private Integer availableQty;
        private LocalDateTime updatedAt;
    }

    @Accessors(chain = true)
    @Data
    public static class AllocationInfo {
        private String transactionId;
        private String shelterId;
        private ItemCategory itemCategory;
        private Integer allocatedAmount;
        private String status;
    }

    @Data
    @NoArgsConstructor
    public static class IncidentListResponse {
        private Integer total;
        private Integer limit;
        private Integer offset;
        private List<IncidentDto> items;
    }

    @Data
    @NoArgsConstructor
    public static class IncidentDto {
        private String incident_id;
        private String incident_type;
        private String severity;
        private String status;
        private LocationDto location;
        private String address_name;
        private String incident_start;
        private Integer report_count;
        private Integer affected_count;
        private String created_at;
    }

    @Data
    @NoArgsConstructor
    public static class LocationDto {
        private String type;
        private List<Double> coordinates;
    }
}