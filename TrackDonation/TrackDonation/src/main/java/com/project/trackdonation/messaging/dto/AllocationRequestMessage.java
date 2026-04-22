package com.project.trackdonation.messaging.dto;

import com.project.trackdonation.entity.ItemCategory;
import lombok.Data;
import java.util.List;

@Data
public class AllocationRequestMessage {
    private String incidentId;
    private String referenceReqId;
    private String requestingUnit;
    private String contactEmail;
    private String shelterId;
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        private ItemCategory itemCategory;
        private String itemName;
        private Integer amountNeeded;
    }
}