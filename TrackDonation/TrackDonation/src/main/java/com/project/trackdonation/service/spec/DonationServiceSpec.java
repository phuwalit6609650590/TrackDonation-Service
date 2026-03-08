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
}