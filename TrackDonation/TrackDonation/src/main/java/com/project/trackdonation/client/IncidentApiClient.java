package com.project.trackdonation.client;

import com.project.trackdonation.exception.IncidentNotFoundException;
import com.project.trackdonation.exception.IncidentServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IncidentApiClient {

    private static final String INCIDENT_API_URL = "http://central-incident-service/api/v1/incidents/%s/status";

    public void verifyIncidentStatus(String incidentId) {

        String targetUrl = String.format(INCIDENT_API_URL, incidentId);
        log.info("[API Client] HTTP GET : {}", targetUrl);

        if (incidentId == null || incidentId.trim().isEmpty()) {
            log.error("[API Client] incident id is null or empty");
            throw new IllegalArgumentException("incidentId is required");
        }

        if ("DOWN".equalsIgnoreCase(incidentId)) {
            log.error("[API Client] Incident Service Timeout 5 s");
            throw new IncidentServiceUnavailableException("Cannot verify incident status at this time");
        }

        if (!incidentId.equals("3e10") && !incidentId.startsWith("INC-")) {
            log.error("[API Client] Incident '{}' is not found", incidentId);
            throw new IncidentNotFoundException("Incident ID '" + incidentId + "' not found in the central system.");
        }

        log.info("[API Client] 200 OK: Incident '{}' is Active", incidentId);
    }
}