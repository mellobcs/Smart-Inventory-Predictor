package com.smartinventory.sip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Root DTO representing the full analytics payload pushed from the Python engine.
 */
public class AlertPayload {

    @NotBlank(message = "Source identifier is required")
    private String source;

    @NotBlank(message = "Generation timestamp is required")
    private String generatedAt;

    @Positive(message = "Period days must be positive")
    private int periodDays;

    @PositiveOrZero(message = "Total items must be non-negative")
    private int totalItemsAnalyzed;

    @PositiveOrZero(message = "Critical alert count must be non-negative")
    private int criticalAlerts;

    @PositiveOrZero(message = "Warning alert count must be non-negative")
    private int warningAlerts;

    @NotNull(message = "Alerts list is required")
    @Valid
    private List<InventoryAlertDto> alerts;

    // --- Getters / Setters ---

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }

    public int getTotalItemsAnalyzed() { return totalItemsAnalyzed; }
    public void setTotalItemsAnalyzed(int totalItemsAnalyzed) { this.totalItemsAnalyzed = totalItemsAnalyzed; }

    public int getCriticalAlerts() { return criticalAlerts; }
    public void setCriticalAlerts(int criticalAlerts) { this.criticalAlerts = criticalAlerts; }

    public int getWarningAlerts() { return warningAlerts; }
    public void setWarningAlerts(int warningAlerts) { this.warningAlerts = warningAlerts; }

    public List<InventoryAlertDto> getAlerts() { return alerts; }
    public void setAlerts(List<InventoryAlertDto> alerts) { this.alerts = alerts; }

    @Override
    public String toString() {
        return "AlertPayload{source='" + source + '\'' +
                ", generatedAt='" + generatedAt + '\'' +
                ", periodDays=" + periodDays +
                ", totalItemsAnalyzed=" + totalItemsAnalyzed +
                ", criticalAlerts=" + criticalAlerts +
                ", warningAlerts=" + warningAlerts +
                ", alertsCount=" + (alerts != null ? alerts.size() : 0) +
                '}';
    }
}