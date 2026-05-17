package com.project.trackdonation.client;

import com.project.trackdonation.exception.IncidentNotFoundException;
import com.project.trackdonation.exception.IncidentServiceUnavailableException;
import com.project.trackdonation.service.spec.DonationServiceSpec.IncidentListResponse;
import com.project.trackdonation.repository.IncidentReferenceRepository;
import com.project.trackdonation.entity.IncidentReference;
import com.project.trackdonation.repository.WarehouseRepository;
import com.project.trackdonation.entity.Warehouse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trackdonation.client.dto.IncidentDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class IncidentApiClient {

    @Autowired
    @Lazy
    private IncidentApiClient self;

    private final RestTemplate restTemplate;
    private final String incidentServiceUrl;
    private final ObjectMapper objectMapper;
    private final IncidentReferenceRepository incidentReferenceRepository;
    private final WarehouseRepository warehouseRepository;

    // Circuit Breaker State
    private LocalDateTime circuitOpenUntil = null;

    public IncidentApiClient(RestTemplate restTemplate, ObjectMapper objectMapper, IncidentReferenceRepository incidentReferenceRepository,
            WarehouseRepository warehouseRepository, @Value("${app.incident-service.url}") String incidentServiceUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.incidentReferenceRepository = incidentReferenceRepository;
        this.warehouseRepository = warehouseRepository;
        this.incidentServiceUrl = incidentServiceUrl;
    }

    public void verifyIncidentStatus(String incidentId) {
        // 1. Check Circuit Breaker
        if (circuitOpenUntil != null) {
            if (LocalDateTime.now().isBefore(circuitOpenUntil)) {
                log.error("[Circuit Breaker] สถานะ: OPEN (ระบบปลายทางล่ม) - ข้ามการเรียก API สำหรับ Incident: {} และส่งคืน Error กลับทันที", incidentId);
                throw new IncidentServiceUnavailableException("Circuit is OPEN. API calls disabled.");
            } else {
                log.info("[Circuit Breaker] หมดเวลาพัก Circuit เข้าสู่สถานะ Half-open");
                circuitOpenUntil = null;
            }
        }

        // 2. Try fetching data (with 1 immediate retry if API fails)
        List<IncidentDto> incidents = null;
        int maxRetries = 2; // รวมรอบแรก + retry อีก 1 ครั้ง = 2 รอบ
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                attempts++;
                incidents = self.fetchBulkIncidents(); // เรียกผ่าน self เพื่อให้ Spring Cache Proxy ทำงาน
                break; // สำเร็จ! ออกจากลูป
            } catch (Exception e) {
                log.warn("[API Client] การเรียก API ล้มเหลวครั้งที่ {} สำหรับ Incident: {} สาเหตุ: {}", attempts, incidentId, e.getMessage());
                if (attempts < maxRetries) {
                    log.info("[API Client] กำลัง Retry เรียก API ใหม่ทันที (รอบที่ 2)...");
                }
            }
        }

        if (incidents == null) {
            // Failed 2 times in the same request!
            circuitOpenUntil = LocalDateTime.now().plusHours(1);
            log.error("[Circuit Breaker] API ล้มเหลวครบ 2 ครั้งใน Request เดียวกัน! เปิด Circuit เป็นเวลา 1 ชั่วโมง!");
            throw new IncidentServiceUnavailableException("Cannot verify incident status. External API is down.");
        }

        // 3. We have the data (from API or Cache). Let's check if the ID exists.
        for (IncidentDto dto : incidents) {
            if (incidentId.equals(dto.getIncidentId())) {
                String status = dto.getStatus();
                if ("VERIFIED".equalsIgnoreCase(status) ||
                        "DISPATCHED".equalsIgnoreCase(status) ||
                        "RESOLVED".equalsIgnoreCase(status) ||
                        "IN_PROGRESS".equalsIgnoreCase(status)) {
                    log.info("[API Client] 200 OK: Incident '{}' ใช้งานได้ (Status: {})", incidentId, status);
                    return;
                } else {
                    log.error("[API Client] Incident '{}' ไม่เปิดรับบริจาค (Status: {})", incidentId, status);
                    throw new IncidentServiceUnavailableException("Incident ID '" + incidentId + "' is not accepting donations. Status: " + status);
                }
            }
        }

        // 4. Not found in the list! The API worked, but the user requested an ID not in the system.
        log.warn("[API Client] ไม่พบ Incident '{}' ในระบบส่วนกลาง (แต่ API ตอบกลับปกติ)", incidentId);
        throw new IncidentNotFoundException("Incident ID '" + incidentId + "' not found in the central system.");
    }

    @Cacheable(value = "bulkIncidents")
    public List<IncidentDto> fetchBulkIncidents() {
        String targetUrl = incidentServiceUrl + "/incidents";
        List<IncidentDto> incidents = new ArrayList<>();
        try {
            log.info("[API Client] Fetching bulk incident list from: {}", targetUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode itemsNode = root.path("items");

                if (itemsNode.isArray()) {
                    for (JsonNode itemNode : itemsNode) {
                        IncidentDto dto = objectMapper.treeToValue(itemNode, IncidentDto.class);
                        incidents.add(dto);
                        
                        // Sync with Local DB
                        try {
                            java.util.Optional<IncidentReference> existing = incidentReferenceRepository.findById(dto.getIncidentId());
                            IncidentReference savedRef;
                            if (existing.isPresent()) {
                                IncidentReference ref = existing.get();
                                if (!dto.getStatus().equals(ref.getStatus())) {
                                    ref.setStatus(dto.getStatus());
                                    ref.setLastUpdatedAt(LocalDateTime.now());
                                    savedRef = incidentReferenceRepository.save(ref);
                                } else {
                                    savedRef = ref;
                                }
                            } else {
                                IncidentReference newRef = new IncidentReference()
                                        .setIncidentId(dto.getIncidentId())
                                        .setStatus(dto.getStatus())
                                        .setLatitude(dto.getLocation() != null && dto.getLocation().getCoordinates() != null && dto.getLocation().getCoordinates().size() >= 2 ? dto.getLocation().getCoordinates().get(1) : null)
                                        .setLongitude(dto.getLocation() != null && dto.getLocation().getCoordinates() != null && dto.getLocation().getCoordinates().size() >= 2 ? dto.getLocation().getCoordinates().get(0) : null)
                                        .setLastUpdatedAt(LocalDateTime.now());
                                savedRef = incidentReferenceRepository.save(newRef);
                            }
                            
                            // Auto-create Warehouse if it doesn't exist
                            if (!warehouseRepository.existsByIncidentId(dto.getIncidentId())) {
                                Warehouse newWh = new Warehouse()
                                        .setWarehouseId("WH-" + dto.getIncidentId() + "-001")
                                        .setIncidentId(dto.getIncidentId())
                                        .setName("main warehouse " + dto.getIncidentId())
                                        .setIsActive(true)
                                        .setCreatedAt(LocalDateTime.now());
                                        
                                if (savedRef.getLatitude() != null && savedRef.getLongitude() != null) {
                                    newWh.setLatitude(savedRef.getLatitude() + 0.054);
                                    newWh.setLongitude(savedRef.getLongitude() + 0.054);
                                } else {
                                    newWh.setLatitude(0.0);
                                    newWh.setLongitude(0.0);
                                }
                                warehouseRepository.save(newWh);
                                log.info("[Auto-Warehouse] สร้างโกดังอัตโนมัติจาก API Sync สำเร็จ: {}", newWh.getWarehouseId());
                            }
                        } catch (Exception dbEx) {
                            log.error("[API Client] Error syncing incident {} to local DB: {}", dto.getIncidentId(), dbEx.getMessage());
                        }
                    }
                }
                log.info("[API Client] Successfully fetched {} incidents", incidents.size());
            } else {
                log.warn("[API Client] Failed to fetch bulk incidents, status code: {}", response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("[API Client] Failed to fetch bulk incidents. HTTP Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new IncidentServiceUnavailableException("Failed to fetch bulk incidents. Status: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("[API Client] Unexpected error fetching bulk incidents: {}", e.getMessage(), e);
            throw new IncidentServiceUnavailableException("Unexpected error fetching bulk incidents: " + e.getMessage());
        }
        return incidents;
    }

    /** ล้าง Cache ทุก 10 นาที ให้สอดคล้องกับ TTL แบบ manual */
    @Scheduled(fixedDelay = 600000)
    @CacheEvict(value = "bulkIncidents", allEntries = true)
    public void evictBulkIncidentsCache() {
        log.info("[Cache] Evicting bulkIncidents cache (Every 10 mins)");
    }
}