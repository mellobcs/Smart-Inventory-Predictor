package com.smartinventory.sip.service;

import com.smartinventory.sip.dto.AlertPayload;
import com.smartinventory.sip.dto.InventoryAlertDto;
import com.smartinventory.sip.entity.InventoryAlert;
import com.smartinventory.sip.repository.InventoryAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business logic service for inventory alert processing.
 * Handles validation, filtering of false positives, persistence, and aggregation.
 */
@Service
public class InventoryAlertService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertService.class);
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private final InventoryAlertRepository repository;

    public InventoryAlertService(InventoryAlertRepository repository) {
        this.repository = repository;
    }

    /**
     * Process the full analytics payload from the Python engine:
     * 1. Validate and transform each alert DTO into an entity.
     * 2. Apply false-positive filtering.
     * 3. Persist valid alerts to the database.
     * 4. Log a formatted system alert summary.
     *
     * @param payload the incoming alert payload
     * @return list of persisted InventoryAlert records
     */
    @Transactional
    public List<InventoryAlert> processAlerts(AlertPayload payload) {
        log.info("Processing alert payload: source={}, periodDays={}, items={}, critical={}, warnings={}",
                payload.getSource(), payload.getPeriodDays(),
                payload.getTotalItemsAnalyzed(), payload.getCriticalAlerts(), payload.getWarningAlerts());

        List<InventoryAlert> persistedAlerts = new ArrayList<>();
        int rejectedCount = 0;

        for (InventoryAlertDto dto : payload.getAlerts()) {
            try {
                // Step 1: Convert DTO → Entity
                InventoryAlert entity = mapToEntity(dto, payload.getSource());

                // Step 2: False-positive filter
                if (isFalsePositive(entity)) {
                    log.debug("Filtered false positive: {} (stock={}, velocity={})",
                            entity.getItemName(), entity.getCurrentStock(), entity.getSalesVelocityVm());
                    rejectedCount++;
                    continue;
                }

                // Step 3: Persist
                InventoryAlert saved = repository.save(entity);
                persistedAlerts.add(saved);

                log.debug("Saved alert: id={}, item={}, alertLevel={}, restockQty={}",
                        saved.getId(), saved.getItemName(), saved.getAlertLevel(), saved.getRecommendedRestockQty());

            } catch (Exception e) {
                log.warn("Failed to process alert for item '{}': {}", dto.getItemName(), e.getMessage());
                rejectedCount++;
            }
        }

        log.info("Alerts processed: persisted={}, rejected={} (false-positives/failures)",
                persistedAlerts.size(), rejectedCount);

        // Output formatted system alert text
        printSystemAlert(persistedAlerts, rejectedCount, payload);

        return persistedAlerts;
    }

    /**
     * Retrieve alert by ID.
     */
    public Optional<InventoryAlert> getAlertById(Long id) {
        return repository.findById(id);
    }

    /**
     * Retrieve all alerts.
     */
    public List<InventoryAlert> getAllAlerts() {
        return repository.findAll();
    }

    /**
     * Retrieve alerts filtered by severity level.
     */
    public List<InventoryAlert> getAlertsByLevel(String alertLevel) {
        return repository.findByAlertLevel(alertLevel);
    }

    /**
     * Retrieve the 10 most recent alerts.
     */
    public List<InventoryAlert> getRecentAlerts() {
        return repository.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * Get total revenue at risk from all critical alerts.
     */
    public double getTotalRevenueAtRisk() {
        return repository.totalRevenueAtRisk();
    }

    /**
     * Get total recommended restock quantity.
     */
    public int getTotalRecommendedRestockQty() {
        return repository.totalRecommendedRestockQty();
    }

    /**
     * Delete an alert by ID.
     */
    @Transactional
    public boolean deleteAlert(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            log.info("Deleted alert id={}", id);
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Map an InventoryAlertDto to an InventoryAlert entity.
     */
    private InventoryAlert mapToEntity(InventoryAlertDto dto, String source) {
        InventoryAlert entity = new InventoryAlert();
        entity.setItemType(dto.getItemType());
        entity.setItemName(dto.getItemName());
        entity.setCurrentStock(dto.getCurrentStock());
        entity.setSalesVelocityVm(dto.getSalesVelocityVm());
        entity.setRunOutRateDays(dto.getRunOutRateDays());
        entity.setPredicted7DayDemand(dto.getPredicted7DayDemand());
        entity.setRevenueAtRisk(dto.getRevenueAtRisk());
        entity.setRecommendedRestockQty(dto.getRecommendedRestockQty());
        entity.setAlertLevel(dto.getAlertLevel());
        entity.setProfitMarginPct(dto.getProfitMarginPct());
        entity.setSource(source);
        entity.setGeneratedAt(parseDateTime(dto.getGeneratedAt()));
        return entity;
    }

    /**
     * Attempt to parse a datetime string using multiple format patterns.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return LocalDateTime.now();
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        log.warn("Could not parse datetime '{}', using current time", dateTimeStr);
        return LocalDateTime.now();
    }

    /**
     * False-positive filter:
     *   - Reject items with zero sales velocity (no sales data → insufficient data).
     *   - Reject items with negative run-out rate (infinite stock).
     *   - Reject CRITICAL alerts if recommended restock qty is 0 (already handled).
     * This prevents noise from polluting the dashboard.
     */
    private boolean isFalsePositive(InventoryAlert entity) {
        if (entity.getSalesVelocityVm() <= 0) {
            return true; // No sales velocity → insufficient data
        }
        if (entity.getRunOutRateDays() < 0) {
            return true; // Negative run-out indicates infinite stock
        }
        if ("CRITICAL_RESTOCK_WARN".equals(entity.getAlertLevel())
                && entity.getRecommendedRestockQty() <= 0) {
            return true; // Not actually critical
        }
        return false;
    }

    /**
     * Print the formatted system alert text (dashboard-ready).
     */
    private void printSystemAlert(List<InventoryAlert> alerts, int rejected, AlertPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║        SMART INVENTORY PREDICTOR — SYSTEM ALERT         ║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Source:   %-42s║\n", payload.getSource()));
        sb.append(String.format("║ Period:   %-42s║\n", payload.getPeriodDays() + " days"));
        sb.append(String.format("║ Analyzed: %-42s║\n", payload.getTotalItemsAnalyzed() + " items"));
        sb.append("╠══════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ CRITICAL: %-42s║\n", payload.getCriticalAlerts() + " ⚠️"));
        sb.append(String.format("║ WARNINGS: %-42s║\n", payload.getWarningAlerts() + ""));
        sb.append(String.format("║ PERSISTED:%-42s║\n", alerts.size() + " alerts"));
        sb.append(String.format("║ REJECTED: %-42s║\n", rejected + " (false-positives)"));
        sb.append("╠══════════════════════════════════════════════════════════╣\n");

        if (!alerts.isEmpty()) {
            sb.append("║ ITEMS REQUIRING IMMEDIATE ATTENTION:                   ║\n");
            for (InventoryAlert alert : alerts) {
                if ("CRITICAL_RESTOCK_WARN".equals(alert.getAlertLevel())) {
                    sb.append(String.format("║   • %-20s [Stock: %-3d → Order %-3d]       ║\n",
                            truncate(alert.getItemName(), 20),
                            alert.getCurrentStock(),
                            alert.getRecommendedRestockQty()));
                }
            }
        }

        double revenueAtRisk = alerts.stream()
                .filter(a -> "CRITICAL_RESTOCK_WARN".equals(a.getAlertLevel()))
                .mapToDouble(InventoryAlert::getRevenueAtRisk)
                .sum();
        sb.append(String.format("║ Revenue at Risk: $%-37.2f║\n", revenueAtRisk));
        sb.append("╚══════════════════════════════════════════════════════════╝\n");

        log.info(sb.toString());
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "null";
        return value.length() <= maxLen ? value : value.substring(0, maxLen - 3) + "...";
    }
}