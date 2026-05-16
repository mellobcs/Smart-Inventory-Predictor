package com.smartinventory.sip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Data Transfer Object for a single inventory alert from the Python engine.
 */
public class InventoryAlertDto {

    @NotBlank(message = "Item type is required")
    private String itemType;

    @NotBlank(message = "Item name is required")
    private String itemName;

    @PositiveOrZero(message = "Current stock must be non-negative")
    private int currentStock;

    @PositiveOrZero(message = "Sales velocity must be non-negative")
    private double salesVelocityVm;

    private double runOutRateDays; // Can be -1 (infinite)

    @PositiveOrZero(message = "Predicted demand must be non-negative")
    private double predicted7DayDemand;

    @PositiveOrZero(message = "Revenue at risk must be non-negative")
    private double revenueAtRisk;

    @PositiveOrZero(message = "Recommended restock qty must be non-negative")
    private int recommendedRestockQty;

    @NotBlank(message = "Alert level is required")
    private String alertLevel;

    private double profitMarginPct;

    @NotBlank(message = "Generation timestamp is required")
    private String generatedAt;

    // --- Getters / Setters ---

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

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    @Override
    public String toString() {
        return "InventoryAlertDto{" +
                "itemType='" + itemType + '\'' +
                ", itemName='" + itemName + '\'' +
                ", currentStock=" + currentStock +
                ", alertLevel='" + alertLevel + '\'' +
                ", recommendedRestockQty=" + recommendedRestockQty +
                '}';
    }
}