package com.smartinventory.sip.repository;

import com.smartinventory.sip.entity.InventoryAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for InventoryAlert entities.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface InventoryAlertRepository extends JpaRepository<InventoryAlert, Long> {

    /** Find all alerts by severity level (CRITICAL_RESTOCK_WARN, OVERSTOCK_WARN, OK). */
    List<InventoryAlert> findByAlertLevel(String alertLevel);

    /** Find alerts for a specific item type (e.g., Hoodie, T-Shirt). */
    List<InventoryAlert> findByItemType(String itemType);

    /** Find alerts for a specific item by name. */
    List<InventoryAlert> findByItemName(String itemName);

    /** Find alerts generated within a date range. */
    List<InventoryAlert> findByGeneratedAtBetween(LocalDateTime start, LocalDateTime end);

    /** Count active critical alerts generated since a given time. */
    long countByAlertLevelAndGeneratedAtAfter(String alertLevel, LocalDateTime after);

    /** Get the most recent N alerts ordered by creation date descending. */
    List<InventoryAlert> findTop10ByOrderByCreatedAtDesc();

    /** Aggregate: sum of revenue at risk for critical alerts. */
    @Query("SELECT COALESCE(SUM(a.revenueAtRisk), 0.0) FROM InventoryAlert a WHERE a.alertLevel = 'CRITICAL_RESTOCK_WARN'")
    double totalRevenueAtRisk();

    /** Aggregate: total recommended restock quantity across all critical alerts. */
    @Query("SELECT COALESCE(SUM(a.recommendedRestockQty), 0) FROM InventoryAlert a WHERE a.alertLevel = 'CRITICAL_RESTOCK_WARN'")
    int totalRecommendedRestockQty();
}