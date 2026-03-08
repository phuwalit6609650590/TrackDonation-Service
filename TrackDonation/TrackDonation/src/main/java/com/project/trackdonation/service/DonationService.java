package com.project.trackdonation.service;

import com.project.trackdonation.service.spec.DonationServiceSpec;
import java.util.List;

public interface DonationService {
    DonationServiceSpec.DonationReceiptInfo recordDonation(DonationServiceSpec.RecordDonationRequest req);
    List<DonationServiceSpec.InventoryInfo> getInventoryByIncident(String incidentId);
}