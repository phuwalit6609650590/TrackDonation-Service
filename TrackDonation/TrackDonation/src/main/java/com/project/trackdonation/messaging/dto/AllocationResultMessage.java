package com.project.trackdonation.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocationResultMessage {
    private String incidentId;
    private String referenceReqId;
    private List<ItemResult> results;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemResult {
        private String itemCategory;
        private String itemName;
        private Integer allocatedAmount;
        private String transactionId;
        private String status;
        private ErrorDetails errorDetails;
    }

    @Data
    @Builder
    public static class ErrorDetails {
        private String errorCode;
        private String errorMessage;
    }
}