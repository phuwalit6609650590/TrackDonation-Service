package com.project.trackdonation.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceAllocationClient {

    private final RestTemplate restTemplate;

    @Value("${app.resource-service.url}")
    private String resourceServiceUrl;

    public boolean requestTransport(Map<String, Object> payload) {
        String url = resourceServiceUrl + "/allocations";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer dispatcher-dev-token");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            log.info("Requesting transport from Resource Service: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully requested transport. Response: {}", response.getBody());
                return true;
            } else {
                log.warn("Failed to request transport. Status: {}, Body: {}", response.getStatusCode(),
                        response.getBody());
                return false;
            }
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            log.error("HTTP error calling ResourceAllocation Service. Status: {}, Response: {}", ex.getStatusCode(),
                    ex.getResponseBodyAsString(), ex);
            return false;
        } catch (Exception ex) {
            log.error("Unexpected error calling ResourceAllocation Service: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
