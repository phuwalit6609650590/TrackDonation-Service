package com.project.trackdonation.service;

import com.project.trackdonation.client.IncidentApiClient;
import com.project.trackdonation.client.dto.IncidentDto;
import com.project.trackdonation.entity.IncidentReference;
import com.project.trackdonation.repository.IncidentReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentSyncService {

    private final IncidentApiClient incidentApiClient;
    private final IncidentReferenceRepository incidentReferenceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IncidentReference> syncAndGet(String targetIncidentId) {
        log.info("Starting Bulk Sync from Incident API...");
        List<IncidentDto> incidents = incidentApiClient.fetchBulkIncidents();

        Optional<IncidentReference> targetResult = Optional.empty();

        for (IncidentDto dto : incidents) {
            boolean isNew = !incidentReferenceRepository.existsById(dto.getIncidentId());
            
            IncidentReference ref = incidentReferenceRepository.findById(dto.getIncidentId())
                    .orElse(new IncidentReference().setIncidentId(dto.getIncidentId()));

            ref.setStatus(dto.getStatus() != null ? dto.getStatus() : "UNKNOWN");
            ref.setLastUpdatedAt(LocalDateTime.now());

            double lat = 0.0;
            double lon = 0.0;
            boolean hasCoordinates = false;

            if (dto.getLocation() != null && dto.getLocation().getCoordinates() != null
                    && dto.getLocation().getCoordinates().size() >= 2) {
                // Coordinates array is [longitude, latitude] based on JSON sample
                lon = dto.getLocation().getCoordinates().get(0);
                lat = dto.getLocation().getCoordinates().get(1);
                ref.setLongitude(lon);
                ref.setLatitude(lat);
                hasCoordinates = true;
            }

            incidentReferenceRepository.save(ref);

            if (isNew && hasCoordinates) {
                log.info("Detected NEW Incident {} with coordinates.", dto.getIncidentId());
            }

            if (targetIncidentId != null && targetIncidentId.equals(dto.getIncidentId())) {
                targetResult = Optional.of(ref);
            }
        }

        log.info("Bulk Sync completed. Synced {} incidents.", incidents.size());

        if (targetResult.isEmpty() && targetIncidentId != null) {
            // Check if it already exists in DB even if not in API response (edge case)
            targetResult = incidentReferenceRepository.findById(targetIncidentId);
        }

        return targetResult;
    }
}
