package com.project.trackdonation.client;

import com.project.trackdonation.exception.IncidentNotFoundException;
import com.project.trackdonation.exception.IncidentServiceUnavailableException;
import com.project.trackdonation.service.spec.DonationServiceSpec.IncidentListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class IncidentApiClient {

    private final RestTemplate restTemplate;
    private final String incidentServiceUrl;
    private final ObjectMapper objectMapper;

    public IncidentApiClient(RestTemplate restTemplate, ObjectMapper objectMapper,
            @Value("${app.incident-service.url}") String incidentServiceUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.incidentServiceUrl = incidentServiceUrl;
    }

    public void verifyIncidentStatus(String incidentId) {
        String targetUrl = incidentServiceUrl + "/incidents";
        try {
            log.info("[API Client] Fetching incident list from: {}", targetUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode itemsNode = root.path("items");

                if (itemsNode.isArray()) {
                    for (JsonNode itemNode : itemsNode) {
                        String id = itemNode.path("incident_id").asText("");
                        if (incidentId.equals(id)) {
                            String status = itemNode.path("status").asText("");

                            if ("VERIFIED".equalsIgnoreCase(status) ||
                                    "DISPATCHED".equalsIgnoreCase(status) ||
                                    "RESOLVED".equalsIgnoreCase(status) ||
                                    "IN_PROGRESS".equalsIgnoreCase(status)) {
                                log.info("[API Client] 200 OK: Incident '{}' is Active (Status: {})", incidentId,
                                        status);
                                return; // Found and valid, exit successfully
                            } else {
                                log.error("[API Client] Incident '{}' is not accepting donations. Status: {}",
                                        incidentId, status);
                                throw new IncidentServiceUnavailableException("Incident ID '" + incidentId
                                        + "' is not accepting donations. Status: " + status);
                            }
                        }
                    }
                }

                // If the loop finishes and we haven't found the incident ID
                log.error("[API Client] Incident '{}' is not found in the external system list", incidentId);
                throw new IncidentNotFoundException(
                        "Incident ID '" + incidentId + "' not found in the central system.");

            } else {
                log.error("[API Client] Failed to fetch incidents, status code: {}", response.getStatusCode());
                throw new IncidentServiceUnavailableException("Cannot verify incident status at this time");
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("[API Client] Incident Service error: {}", e.getStatusCode());
            throw new IncidentServiceUnavailableException("Incident Service is currently unavailable.");
        } catch (IncidentNotFoundException | IncidentServiceUnavailableException e) {
            throw e; // Rethrow business exceptions
        } catch (Exception e) {
            log.error("[API Client] Incident Service Timeout or Error", e);
            throw new IncidentServiceUnavailableException("Cannot verify incident status at this time");
        }
    }
}