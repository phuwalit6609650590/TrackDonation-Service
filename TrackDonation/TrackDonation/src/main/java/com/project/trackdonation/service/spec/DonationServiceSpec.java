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
        private String warehouseId;
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

    @Data
    @Accessors(chain = true)
    public static class AllocationInfo {
        private String transactionId;
        private ItemCategory itemCategory;
        private Integer allocatedAmount;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class AllocationHistoryInfo {
        private String referenceReqId;
        private String incidentId;
        private ItemCategory itemCategory;
        private String itemName;
        private Integer allocatedAmount;
        private String status;
        private String destinationLat;
        private String destinationLong;
        private String warehouseId;
        private java.time.LocalDateTime createdAt;
        private Long queuePosition; // null if not in queue
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