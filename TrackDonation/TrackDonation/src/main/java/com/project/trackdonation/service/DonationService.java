package com.project.trackdonation.service;

import com.project.trackdonation.entity.AllocationRecord;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import java.util.List;

public interface DonationService {
    DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req);
    List<DonationServiceSpec.InventoryInfo> getInventoryByIncident(String incidentId);
    AllocationRecord allocateItem(AllocationRequestMessage request, String messageId);
}