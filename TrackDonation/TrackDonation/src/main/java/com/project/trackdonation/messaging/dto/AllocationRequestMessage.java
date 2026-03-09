package com.project.trackdonation.messaging.dto;

import com.project.trackdonation.entity.ItemCategory;
import lombok.Data;

@Data
public class AllocationRequestMessage {
    private String incidentId;
    private String referenceReqId;
    private ItemCategory itemCategory;
    private String itemName;
    private Integer amountNeeded;
    private String requestingUnit;
}