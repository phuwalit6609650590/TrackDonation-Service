package com.project.trackdonation.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseFixer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void fixEnumConstraint() {
        try {
            log.info("Attempting to fix inventory_states_status_check constraint...");
            jdbcTemplate.execute("ALTER TABLE inventory_states DROP CONSTRAINT IF EXISTS inventory_states_status_check");
            log.info("✅ Successfully dropped inventory_states_status_check constraint!");
        } catch (Exception e) {
            log.warn("Could not drop constraint (it may not exist or DB user lacks permission): {}", e.getMessage());
        }
    }
}
