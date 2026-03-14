package com.project.trackdonation.controller;

import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/donations")
@RequiredArgsConstructor
public class DonationController {

    private final DonationService donationService;

    @PostMapping
    public ResponseEntity<DonationServiceSpec.DonationReceiptInfo> recordDonation(
            @RequestBody DonationServiceSpec.RecordDonationRequest req) {
        DonationServiceSpec.DonationReceiptInfo receipt = donationService.recordDonation(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @GetMapping("/inventory/{incidentId}")
    public ResponseEntity<List<DonationServiceSpec.InventoryInfo>> getInventoryByIncident(
            @PathVariable String incidentId) {
        List<DonationServiceSpec.InventoryInfo> inventory = donationService.getInventoryByIncident(incidentId);
        return ResponseEntity.ok(inventory);
    }
}