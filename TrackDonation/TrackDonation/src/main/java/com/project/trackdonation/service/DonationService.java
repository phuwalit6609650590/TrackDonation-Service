package com.project.trackdonation.service;

import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import java.util.List;

public interface DonationService {
    DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req);
    List<DonationServiceSpec.InventoryInfo> getInventoryByIncident(String incidentId);
    List<AllocationRecord> allocateItems(AllocationRequestMessage request, String messageId);
    List<DonationServiceSpec.AllocationInfo> getAllocations(String incidentId);
    org.springframework.data.domain.Page<DonationServiceSpec.AllocationHistoryInfo> getAllocationHistory(String query, String incidentId, org.springframework.data.domain.Pageable pageable);

    void cancelAndRefundAllocation(String referenceReqId);
    void dispatchSelfPickup(String referenceReqId);
}