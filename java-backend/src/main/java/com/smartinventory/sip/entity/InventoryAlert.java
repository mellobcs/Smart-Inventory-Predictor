package com.smartinventory.sip.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a persisted inventory alert record.
 * Maps to the 'inventory_alerts' table in the relational database.
 */
@Entity
@Table(name = "inventory_alerts", indexes = {
    @Index(name = "idx_alert_level", columnList = "alertLevel"),
    @Index(name = "idx_item_name", columnList = "itemName"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class InventoryAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String itemType;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private int currentStock;

    @Column(nullable = false)
    private double salesVelocityVm;

    @Column(nullable = false)
    private double runOutRateDays;

    @Column(nullable = false)
    private double predicted7DayDemand;

    @Column(nullable = false)
    private double revenueAtRisk;

    @Column(nullable = false)
    private int recommendedRestockQty;

    @Column(nullable = false, length = 30)
    private String alertLevel;

    @Column(nullable = false)
    private double profitMarginPct;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.source == null || this.source.isBlank()) {
            this.source = "SmartInventoryPredictor-PythonEngine";
        }
    }

    // --- Getters / Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }

    public double getSalesVelocityVm() { return salesVelocityVm; }
    public void setSalesVelocityVm(double salesVelocityVm) { this.salesVelocityVm = salesVelocityVm; }

    public double getRunOutRateDays() { return runOutRateDays; }
    public void setRunOutRateDays(double runOutRateDays) { this.runOutRateDays = runOutRateDays; }

    public double getPredicted7DayDemand() { return predicted7DayDemand; }
    public void setPredicted7DayDemand(double predicted7DayDemand) { this.predicted7DayDemand = predicted7DayDemand; }

    public double getRevenueAtRisk() { return revenueAtRisk; }
    public void setRevenueAtRisk(double revenueAtRisk) { this.revenueAtRisk = revenueAtRisk; }

    public int getRecommendedRestockQty() { return recommendedRestockQty; }
    public void setRecommendedRestockQty(int recommendedRestockQty) { this.recommendedRestockQty = recommendedRestockQty; }

    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }

    public double getProfitMarginPct() { return profitMarginPct; }
    public void setProfitMarginPct(double profitMarginPct) { this.profitMarginPct = profitMarginPct; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "InventoryAlert{" +
                "id=" + id +
                ", itemName='" + itemName + '\'' +
                ", alertLevel='" + alertLevel + '\'' +
                ", recommendedRestockQty=" + recommendedRestockQty +
                ", createdAt=" + createdAt +
                '}';
    }
}