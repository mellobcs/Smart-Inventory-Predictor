package com.smartinventory.sip.controller;

import com.smartinventory.sip.dto.AlertPayload;
import com.smartinventory.sip.entity.InventoryAlert;
import com.smartinventory.sip.exception.ResourceNotFoundException;
import com.smartinventory.sip.service.InventoryAlertService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing endpoints for the Python Analytics Engine
 * and dashboard queries.
 *
 * POST /api/inventory/alerts   — Receive analytics payload from Python engine
 * GET  /api/inventory/alerts   — Retrieve all persisted alerts
 * GET  /api/inventory/alerts/{id} — Get a single alert by ID
 * GET  /api/inventory/alerts/level/{level} — Filter by severity
 * GET  /api/inventory/alerts/recent — Get 10 most recent alerts
 * GET  /api/inventory/alerts/summary — Get aggregate summary stats
 * DELETE /api/inventory/alerts/{id} — Delete an alert
 */
@RestController
@RequestMapping("/api/inventory/alerts")
public class InventoryAlertController {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertController.class);

    private final InventoryAlertService alertService;

    public InventoryAlertController(InventoryAlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * POST endpoint — The cross-language bridge.
     * Python engine pushes its analytics payload here.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveAlerts(@Valid @RequestBody AlertPayload payload) {
        log.info("Received analytics payload from Python engine: {} items, {} critical",
                payload.getTotalItemsAnalyzed(), payload.getCriticalAlerts());

        List<InventoryAlert> persisted = alertService.processAlerts(payload);

        Map<String, Object> response = Map.of(
                "status", "success",
                "message", "Alerts processed successfully",
                "persistedCount", persisted.size(),
                "totalReceived", payload.getAlerts() != null ? payload.getAlerts().size() : 0
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET all alerts.
     */
    @GetMapping
    public ResponseEntity<List<InventoryAlert>> getAllAlerts() {
        List<InventoryAlert> alerts = alertService.getAllAlerts();
        return ResponseEntity.ok(alerts);
    }

    /**
     * GET alert by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryAlert> getAlertById(@PathVariable Long id) {
        InventoryAlert alert = alertService.getAlertById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryAlert", id));
        return ResponseEntity.ok(alert);
    }

    /**
     * GET alerts filtered by severity level.
     */
    @GetMapping("/level/{level}")
    public ResponseEntity<List<InventoryAlert>> getAlertsByLevel(@PathVariable String level) {
        List<InventoryAlert> alerts = alertService.getAlertsByLevel(level.toUpperCase());
        return ResponseEntity.ok(alerts);
    }

    /**
     * GET 10 most recent alerts.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<InventoryAlert>> getRecentAlerts() {
        List<InventoryAlert> alerts = alertService.getRecentAlerts();
        return ResponseEntity.ok(alerts);
    }

    /**
     * GET aggregate summary statistics for the dashboard.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        List<InventoryAlert> allAlerts = alertService.getAllAlerts();
        double totalRevenueAtRisk = alertService.getTotalRevenueAtRisk();
        int totalRestockQty = alertService.getTotalRecommendedRestockQty();

        long criticalCount = allAlerts.stream()
                .filter(a -> "CRITICAL_RESTOCK_WARN".equals(a.getAlertLevel()))
                .count();
        long warningCount = allAlerts.stream()
                .filter(a -> "OVERSTOCK_WARN".equals(a.getAlertLevel()))
                .count();

        Map<String, Object> summary = Map.of(
                "totalAlerts", allAlerts.size(),
                "criticalAlerts", criticalCount,
                "warningAlerts", warningCount,
                "totalRevenueAtRisk", Math.round(totalRevenueAtRisk * 100.0) / 100.0,
                "totalRecommendedRestockQty", totalRestockQty,
                "lastUpdated", allAlerts.isEmpty() ? "N/A" : allAlerts.get(0).getCreatedAt().toString()
        );

        return ResponseEntity.ok(summary);
    }

    /**
     * DELETE alert by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAlert(@PathVariable Long id) {
        boolean deleted = alertService.deleteAlert(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Alert deleted successfully"));
        }
        throw new ResourceNotFoundException("InventoryAlert", id);
    }
}