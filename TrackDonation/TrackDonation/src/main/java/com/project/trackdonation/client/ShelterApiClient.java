package com.project.trackdonation.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ShelterApiClient {

    private final RestTemplate restTemplate;
    private final String shelterServiceUrl;
    private final ObjectMapper objectMapper;

    public ShelterApiClient(RestTemplate restTemplate, ObjectMapper objectMapper,
            @Value("${app.shelter-service.url}") String shelterServiceUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.shelterServiceUrl = shelterServiceUrl;
    }

    public boolean verifyShelterExists(String shelterId) {
        if (shelterId == null || shelterId.trim().isEmpty()) {
            return false;
        }

        try {
            String url = shelterServiceUrl + "/shelters/" + shelterId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // Directly check the shelter_id field in the response object
                if (shelterId.equals(root.path("shelter_id").asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // For resilience, if the service is down or returns 404, we assume the shelter
            // is invalid or inaccessible
            // We could throw an exception if we want to fail the allocation explicitly due
            // to timeout
            return false;
        }
    }
}
