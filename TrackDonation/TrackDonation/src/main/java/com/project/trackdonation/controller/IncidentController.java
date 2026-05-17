package com.project.trackdonation.controller;

import com.project.trackdonation.entity.IncidentReference;
import com.project.trackdonation.repository.IncidentReferenceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Slf4j
public class IncidentController {

    private final IncidentReferenceRepository incidentReferenceRepository;

    private static final List<String> ALLOWED_STATUSES = Arrays.asList(
            "VERIFIED", "IN_PROGRESS", "RESOLVED", "CLOSED", 
            "REJECTED", "DISPATCHED", "REPORTED", "PENDING_ASSIGNMENT"
    );

    @Data
    public static class UpdateIncidentStatusRequest {
        private String status;
    }

    @GetMapping
    public ResponseEntity<List<IncidentReference>> getAllIncidents() {
        List<IncidentReference> incidents = incidentReferenceRepository.findAll();
        return ResponseEntity.ok(incidents);
    }

    @PutMapping("/{incidentId}/status")
    public ResponseEntity<?> updateIncidentStatus(@PathVariable String incidentId, @RequestBody UpdateIncidentStatusRequest request) {
        String newStatus = request.getStatus();
        
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Status cannot be empty");
        }
        
        newStatus = newStatus.trim().toUpperCase();

        if (!ALLOWED_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body("Invalid status. Allowed statuses are: " + ALLOWED_STATUSES);
        }

        Optional<IncidentReference> refOpt = incidentReferenceRepository.findById(incidentId);
        if (refOpt.isEmpty()) {
            log.warn("[Incident API] ไม่พบ Incident {} ในระบบ Local DB", incidentId);
            return ResponseEntity.notFound().build();
        }

        IncidentReference ref = refOpt.get();
        String oldStatus = ref.getStatus();
        
        ref.setStatus(newStatus);
        ref.setLastUpdatedAt(LocalDateTime.now());
        incidentReferenceRepository.save(ref);

        log.info("[Incident API] เปลี่ยนสถานะ Incident {} จาก {} เป็น {} เรียบร้อยแล้ว", incidentId, oldStatus, newStatus);
        
        return ResponseEntity.ok().body("Incident status updated successfully from " + oldStatus + " to " + newStatus);
    }
    @PutMapping("/{incidentId}")
    public ResponseEntity<?> updateIncident(@PathVariable String incidentId, @RequestBody IncidentReference request) {
        Optional<IncidentReference> refOpt = incidentReferenceRepository.findById(incidentId);
        if (refOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        IncidentReference ref = refOpt.get();
        
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            String newStatus = request.getStatus().trim().toUpperCase();
            if (ALLOWED_STATUSES.contains(newStatus)) {
                ref.setStatus(newStatus);
            }
        }
        
        if (request.getLatitude() != null) ref.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) ref.setLongitude(request.getLongitude());
        
        ref.setLastUpdatedAt(LocalDateTime.now());
        incidentReferenceRepository.save(ref);

        log.info("[Incident API] อัปเดตข้อมูล Incident {} เรียบร้อยแล้ว", incidentId);
        return ResponseEntity.ok(ref);
    }

    @DeleteMapping("/{incidentId}")
    public ResponseEntity<?> deleteIncident(@PathVariable String incidentId) {
        Optional<IncidentReference> refOpt = incidentReferenceRepository.findById(incidentId);
        if (refOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        incidentReferenceRepository.deleteById(incidentId);
        log.info("[Incident API] ลบ Incident {} เรียบร้อยแล้ว", incidentId);
        return ResponseEntity.noContent().build();
    }
}
