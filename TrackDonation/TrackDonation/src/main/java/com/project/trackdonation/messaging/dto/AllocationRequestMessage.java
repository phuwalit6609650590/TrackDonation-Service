package com.project.trackdonation.messaging.dto;

import com.project.trackdonation.entity.ItemCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class AllocationRequestMessage {

    @NotBlank(message = "incidentId is required")
    private String incidentId;

    @NotBlank(message = "warehouseId is required")
    private String warehouseId;

    private String referenceReqId;

    @NotBlank(message = "requestingUnit is required")
    private String requestingUnit;

    private String contactEmail;
    private Double destinationLat;
    private Double destinationLong;
    private Boolean isSelfPickup = false;

    @Valid
    @NotEmpty(message = "items must not be empty")
    private List<ItemRequest> items;

    @AssertTrue(message = "destinationLat and destinationLong are required when isSelfPickup is false")
    private boolean isDestinationValidForDelivery() {
        if (Boolean.TRUE.equals(isSelfPickup)) return true;
        return destinationLat != null && destinationLong != null;
    }

    @Data
    public static class ItemRequest {

        @NotNull(message = "itemCategory is required")
        private ItemCategory itemCategory;

        @NotBlank(message = "itemName is required")
        private String itemName;

        @NotNull(message = "amountNeeded is required")
        @Min(value = 1, message = "amountNeeded must be at least 1")
        private Integer amountNeeded;
    }
}