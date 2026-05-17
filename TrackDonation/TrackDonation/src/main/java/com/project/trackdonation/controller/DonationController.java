package com.project.trackdonation.controller;

import com.project.trackdonation.service.DonationService;
import com.project.trackdonation.service.spec.DonationServiceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import com.project.trackdonation.messaging.AllocationPublisher;
import com.project.trackdonation.messaging.dto.AllocationRequestMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class DonationController {

    private final DonationService donationService;
    private final AllocationPublisher allocationPublisher;

    @PostMapping("/donations")
    public ResponseEntity<DonationServiceSpec.DonationReceiptInfo> recordDonation(
            @RequestBody DonationServiceSpec.RecordDonationRequest req) {
        log.info("[HTTP] POST /api/donations - รับคำขอบริจาคจาก: {} (Incident: {}, Warehouse: {})", 
                 req.getDonorName(), req.getIncidentId(), req.getWarehouseId());
        DonationServiceSpec.DonationReceiptInfo receipt = donationService.recordDonation(req);
        log.info("[HTTP] POST /api/donations - บันทึกสำเร็จ (DonationID: {})", receipt.getDonationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @GetMapping("/inventory/{incidentId}")
    public ResponseEntity<List<DonationServiceSpec.InventoryInfo>> getInventoryByIncident(
            @PathVariable String incidentId) {
        log.info("[HTTP] GET /api/inventory/{} - ตรวจสอบสต็อกสินค้า", incidentId);
        List<DonationServiceSpec.InventoryInfo> inventory = donationService.getInventoryByIncident(incidentId);
        log.info("[HTTP] GET /api/inventory/{} - คืนค่าสต็อกจำนวน {} รายการ", incidentId, inventory.size());
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/allocations")
    public ResponseEntity<List<DonationServiceSpec.AllocationInfo>> getAllocations(
            @RequestParam(required = false) String incidentId) {
        log.info("[HTTP] GET /api/allocations - ตรวจสอบข้อมูลการเบิกของ (Incident Filter: {})", incidentId);
        List<DonationServiceSpec.AllocationInfo> allocations = donationService.getAllocations(incidentId);
        return ResponseEntity.ok(allocations);
    }

    @GetMapping("/allocations/history")
    public ResponseEntity<Page<DonationServiceSpec.AllocationHistoryInfo>> getAllocationHistory(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false) String incidentId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("[HTTP] GET /api/allocations/history - ดึงประวัติการเบิกของ (Query: {}, Incident: {}, Page: {})", query, incidentId, pageable.getPageNumber());
        Page<DonationServiceSpec.AllocationHistoryInfo> history = donationService.getAllocationHistory(query, incidentId, pageable);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/allocations")
    public ResponseEntity<Map<String, Object>> requestAllocation(
            @Valid @RequestBody AllocationRequestMessage request) {
        if (request.getReferenceReqId() == null || request.getReferenceReqId().isBlank()) {
            request.setReferenceReqId("REQ-TD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        log.info("[HTTP] POST /api/allocations - รับคำขอเบิกของจาก: {} (ReqID: {}, Incident: {})", 
                 request.getRequestingUnit(), request.getReferenceReqId(), request.getIncidentId());
        allocationPublisher.publishAllocationRequest(request);
        log.info("[HTTP] POST /api/allocations - นำคำขอลงคิว (MQ) สำเร็จ");
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Allocation request submitted successfully",
                "referenceReqId", request.getReferenceReqId()
        ));
    }

    @PostMapping("/allocations/{referenceReqId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAllocation(@PathVariable String referenceReqId) {
        log.info("[HTTP] POST /api/allocations/{}/cancel - รับคำขอยกเลิกจาก Admin", referenceReqId);
        try {
            donationService.cancelAndRefundAllocation(referenceReqId);
            log.info("[HTTP] POST /api/allocations/{}/cancel - ยกเลิกและคืนของเข้าสต็อกสำเร็จ", referenceReqId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Allocation cancelled and items refunded to inventory",
                    "referenceReqId", referenceReqId
            ));
        } catch (IllegalArgumentException e) {
            log.error("[HTTP] POST /api/allocations/{}/cancel - ไม่พบคำขอเบิกของนี้", referenceReqId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            log.error("[HTTP] POST /api/allocations/{}/cancel - ไม่สามารถยกเลิกได้ ({})", referenceReqId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/allocations/{referenceReqId}/dispatch-pickup")
    public ResponseEntity<Map<String, Object>> dispatchPickupAllocation(@PathVariable String referenceReqId) {
        log.info("[HTTP] POST /api/allocations/{}/dispatch-pickup - รับคำขอเปลี่ยนสถานะรับของเองเป็น DISPATCHED", referenceReqId);
        try {
            donationService.dispatchSelfPickup(referenceReqId);
            log.info("[HTTP] POST /api/allocations/{}/dispatch-pickup - อัปเดตสถานะเป็น DISPATCHED สำเร็จ", referenceReqId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Allocation status updated to DISPATCHED",
                    "referenceReqId", referenceReqId
            ));
        } catch (IllegalArgumentException e) {
            log.error("[HTTP] POST /api/allocations/{}/dispatch-pickup - ไม่พบคำขอเบิกของนี้", referenceReqId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            log.error("[HTTP] POST /api/allocations/{}/dispatch-pickup - ไม่สามารถทำรายการได้ ({})", referenceReqId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", e.getMessage()
            ));
        }
    }
}