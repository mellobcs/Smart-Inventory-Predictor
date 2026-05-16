#!/usr/bin/env python3
"""
Smart Inventory Predictor (SIP) - Python Analytics Engine
=========================================================
Ingests transaction data, calculates sales velocity and run-out rates,
generates restock alerts, and pushes predictions to the Java Spring Boot backend.

Mathematical Models:
  - Vm (Moving Average Sales Velocity) = avg(units_sold) over window / time_period
  - Ro (Run-Out Rate) = current_stock / Vm  (days until stock = 0)
  - Critical Alert: stock_on_hand < (Vm * 7)  → predicted 7-day demand
"""

import json
import os
import sys
import warnings
from datetime import datetime, date
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
import requests
from scipy import stats as scipy_stats

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DATA_PATH = os.path.join(os.path.dirname(__file__), "data", "sample_sales.csv")
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output")
VISUALIZATION_DIR = os.path.join(OUTPUT_DIR, "charts")

JAVA_BACKEND_URL = "http://localhost:8080/api/inventory/alerts"

# Restock threshold multiplier (in days of predicted demand)
RESTOCK_DAYS_THRESHOLD = 7

# Alert severity levels
SEVERITY_CRITICAL = "CRITICAL_RESTOCK_WARN"
SEVERITY_WARNING = "OVERSTOCK_WARN"
SEVERITY_OK = "OK"

# ---------------------------------------------------------------------------
# Utility functions
# ---------------------------------------------------------------------------

def ensure_directories() -> None:
    """Create output directories if they don't exist."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(VISUALIZATION_DIR, exist_ok=True)


def load_sales_data(filepath: str) -> pd.DataFrame:
    """
    Load sales transaction CSV into a DataFrame.
    Validates required columns and coerces dtypes.
    """
    required_cols = {
        "transaction_id", "date", "item_type", "item_name",
        "units_sold", "unit_price", "cost_price",
        "stock_on_hand", "reorder_point"
    }

    if not os.path.exists(filepath):
        print(f"[ERROR] Data file not found: {filepath}")
        sys.exit(1)

    df = pd.read_csv(filepath, parse_dates=["date"])

    missing = required_cols - set(df.columns)
    if missing:
        print(f"[ERROR] Missing required columns: {missing}")
        sys.exit(1)

    # Ensure numeric dtypes
    df["units_sold"] = pd.to_numeric(df["units_sold"], errors="coerce")
    df["unit_price"] = pd.to_numeric(df["unit_price"], errors="coerce")
    df["cost_price"] = pd.to_numeric(df["cost_price"], errors="coerce")
    df["stock_on_hand"] = pd.to_numeric(df["stock_on_hand"], errors="coerce")
    df["reorder_point"] = pd.to_numeric(df["reorder_point"], errors="coerce")

    # Drop rows with any NaN in critical columns
    before = len(df)
    df = df.dropna(subset=["units_sold", "stock_on_hand"])
    dropped = before - len(df)
    if dropped:
        print(f"[WARN] Dropped {dropped} rows with missing data.")

    print(f"[INFO] Loaded {len(df)} transactions from {filepath}")
    return df


# ---------------------------------------------------------------------------
# Core Analytics
# ---------------------------------------------------------------------------

def compute_sales_velocity(df: pd.DataFrame) -> pd.DataFrame:
    """
    Group by item_type & item_name, compute:
      - Vm (Moving Average Sales Velocity): average daily units sold
      - daily_std: standard deviation of daily sales
      - total_sold: aggregate units sold over the period
      - days_in_data: number of unique days in the dataset
    """
    grouped = df.groupby(["item_type", "item_name"], as_index=False).agg(
        total_sold=("units_sold", "sum"),
        daily_std=("units_sold", "std"),
        transaction_count=("transaction_id", "count"),
        current_stock=("stock_on_hand", "last"),
        reorder_point=("reorder_point", "last"),
        unit_price=("unit_price", "last"),
        cost_price=("cost_price", "last"),
    )

    # Number of unique days (time period)
    unique_days = df["date"].nunique()
    grouped["days_in_data"] = unique_days

    # Vm = total_sold / days_in_data  (average daily sales velocity)
    grouped["sales_velocity_vm"] = grouped["total_sold"] / grouped["days_in_data"]

    # Fill NaN std with 0 (only one transaction for that item)
    grouped["daily_std"] = grouped["daily_std"].fillna(0.0)

    return grouped


def compute_run_out_rate(velocity_df: pd.DataFrame) -> pd.DataFrame:
    """
    Compute Ro (Run-Out Rate):
      Ro = current_stock / Vm
    This gives the number of days until stock runs out at current velocity.

    Also compute:
      - predicted_7day_demand = Vm * 7
      - revenue_at_risk = predicted_7day_demand * unit_price
    """
    # Avoid division by zero
    velocity_df["run_out_rate_days"] = np.where(
        velocity_df["sales_velocity_vm"] > 0,
        velocity_df["current_stock"] / velocity_df["sales_velocity_vm"],
        np.inf  # No sales → stock never runs out
    )

    velocity_df["predicted_7day_demand"] = (
        velocity_df["sales_velocity_vm"] * RESTOCK_DAYS_THRESHOLD
    )
    velocity_df["revenue_at_risk"] = (
        velocity_df["predicted_7day_demand"] * velocity_df["unit_price"]
    )
    velocity_df["cost_of_goods_at_risk"] = (
        velocity_df["predicted_7day_demand"] * velocity_df["cost_price"]
    )

    return velocity_df


def classify_alert_level(row: pd.Series) -> str:
    """
    Determine the alert severity for an item row:
      - CRITICAL_RESTOCK_WARN: stock < predicted 7-day demand
      - OVERSTOCK_WARN: stock > 3x predicted 7-day demand (capital tied up)
      - OK: otherwise
    """
    stock = row["current_stock"]
    predicted = row["predicted_7day_demand"]

    if stock < predicted:
        return SEVERITY_CRITICAL
    elif stock > 3 * predicted and predicted > 0:
        return SEVERITY_WARNING
    else:
        return SEVERITY_OK


def generate_alerts(velocity_df: pd.DataFrame) -> pd.DataFrame:
    """Apply alert classification and enrich with actionable information."""
    velocity_df["alert_level"] = velocity_df.apply(classify_alert_level, axis=1)
    velocity_df["recommended_restock_qty"] = np.where(
        velocity_df["alert_level"] == SEVERITY_CRITICAL,
        (velocity_df["predicted_7day_demand"] - velocity_df["current_stock"]).clip(lower=0).astype(int),
        0
    )
    velocity_df["generated_at"] = datetime.now().isoformat()
    velocity_df["profit_margin_pct"] = np.where(
        velocity_df["unit_price"] > 0,
        ((velocity_df["unit_price"] - velocity_df["cost_price"]) / velocity_df["unit_price"]) * 100,
        0.0
    )
    return velocity_df


# ---------------------------------------------------------------------------
# Visualization
# ---------------------------------------------------------------------------

def generate_charts(velocity_df: pd.DataFrame, df_raw: pd.DataFrame) -> List[str]:
    """
    Create diagnostic visualizations:
      1. Sales velocity bar chart per item
      2. Stock vs predicted demand comparison
      3. Daily sales trend for critical items
    Returns list of saved file paths.
    """
    chart_files = []

    try:
        import matplotlib
        matplotlib.use("Agg")  # Non-interactive backend
        import matplotlib.pyplot as plt
        import seaborn as sns

        sns.set_theme(style="whitegrid")
        plt.rcParams["figure.figsize"] = (12, 6)

        # --- Chart 1: Sales Velocity per Item ---
        fig1, ax1 = plt.subplots()
        items = velocity_df["item_name"]
        velocities = velocity_df["sales_velocity_vm"]
        colors = velocity_df["alert_level"].map({
            SEVERITY_CRITICAL: "red",
            SEVERITY_WARNING: "orange",
            SEVERITY_OK: "green"
        })
        bars = ax1.bar(items, velocities, color=colors, edgecolor="black", linewidth=0.5)
        ax1.set_xlabel("Item")
        ax1.set_ylabel("Avg Daily Sales Velocity (units/day)")
        ax1.set_title("Sales Velocity by Item (Red=Critical, Orange=Warning, Green=OK)")
        ax1.tick_params(axis="x", rotation=45)
        fig1.tight_layout()
        path1 = os.path.join(VISUALIZATION_DIR, "sales_velocity.png")
        fig1.savefig(path1, dpi=150)
        plt.close(fig1)
        chart_files.append(path1)
        print(f"[INFO] Saved chart: {path1}")

        # --- Chart 2: Stock vs Predicted 7-Day Demand ---
        fig2, ax2 = plt.subplots()
        x = np.arange(len(items))
        width = 0.35
        ax2.bar(x - width/2, velocity_df["current_stock"], width, label="Current Stock", color="steelblue")
        ax2.bar(x + width/2, velocity_df["predicted_7day_demand"], width, label="Predicted 7-Day Demand", color="coral")
        ax2.set_xlabel("Item")
        ax2.set_ylabel("Units")
        ax2.set_title("Current Stock vs Predicted 7-Day Demand")
        ax2.set_xticks(x)
        ax2.set_xticklabels(items, rotation=45, ha="right")
        ax2.legend()
        fig2.tight_layout()
        path2 = os.path.join(VISUALIZATION_DIR, "stock_vs_demand.png")
        fig2.savefig(path2, dpi=150)
        plt.close(fig2)
        chart_files.append(path2)
        print(f"[INFO] Saved chart: {path2}")

        # --- Chart 3: Daily Sales Trend for Critical Items ---
        critical_items = velocity_df[velocity_df["alert_level"] == SEVERITY_CRITICAL]["item_name"].tolist()
        if critical_items:
            fig3, ax3 = plt.subplots()
            daily = df_raw[df_raw["item_name"].isin(critical_items)]
            daily_pivot = daily.pivot_table(
                index="date", columns="item_name", values="units_sold", aggfunc="sum"
            ).fillna(0)
            daily_pivot.plot(ax=ax3, marker="o", linewidth=2)
            ax3.set_xlabel("Date")
            ax3.set_ylabel("Units Sold")
            ax3.set_title(f"Daily Sales Trend for Critical Restock Items")
            ax3.legend(title="Item")
            fig3.tight_layout()
            path3 = os.path.join(VISUALIZATION_DIR, "critical_items_trend.png")
            fig3.savefig(path3, dpi=150)
            plt.close(fig3)
            chart_files.append(path3)
            print(f"[INFO] Saved chart: {path3}")

    except ImportError as e:
        print(f"[WARN] Visualization libraries not available: {e}")
        print("[WARN] Skipping chart generation.")
    except Exception as e:
        print(f"[WARN] Chart generation failed: {e}")

    return chart_files


# ---------------------------------------------------------------------------
# JSON Payload Generation
# ---------------------------------------------------------------------------

def build_alert_payload(velocity_df: pd.DataFrame) -> Dict:
    """
    Build the structured JSON payload to send to the Java backend.
    """
    alerts = []
    for _, row in velocity_df.iterrows():
        alert = {
            "itemType": row["item_type"],
            "itemName": row["item_name"],
            "currentStock": int(row["current_stock"]),
            "salesVelocityVm": round(row["sales_velocity_vm"], 2),
            "runOutRateDays": round(row["run_out_rate_days"], 1) if np.isfinite(row["run_out_rate_days"]) else -1.0,
            "predicted7DayDemand": round(row["predicted_7day_demand"], 1),
            "revenueAtRisk": round(row["revenue_at_risk"], 2),
            "recommendedRestockQty": int(row["recommended_restock_qty"]),
            "alertLevel": row["alert_level"],
            "profitMarginPct": round(row["profit_margin_pct"], 1),
            "generatedAt": row["generated_at"]
        }
        alerts.append(alert)

    payload = {
        "source": "SmartInventoryPredictor-PythonEngine",
        "generatedAt": datetime.now().isoformat(),
        "periodDays": int(velocity_df["days_in_data"].iloc[0]),
        "totalItemsAnalyzed": len(velocity_df),
        "criticalAlerts": int((velocity_df["alert_level"] == SEVERITY_CRITICAL).sum()),
        "warningAlerts": int((velocity_df["alert_level"] == SEVERITY_WARNING).sum()),
        "alerts": alerts
    }
    return payload


def save_payload_json(payload: Dict) -> str:
    """Write the payload to a local JSON file as a backup."""
    path = os.path.join(OUTPUT_DIR, "analytics_report.json")
    with open(path, "w") as f:
        json.dump(payload, f, indent=2)
    print(f"[INFO] Report saved to {path}")
    return path


# ---------------------------------------------------------------------------
# REST API Integration (Cross-Language Bridge)
# ---------------------------------------------------------------------------

def push_to_backend(payload: Dict) -> bool:
    """
    POST the analytics payload to the Java Spring Boot backend.
    Returns True on success, False otherwise.
    """
    try:
        print(f"[INFO] POSTing analytics to {JAVA_BACKEND_URL}")
        response = requests.post(
            JAVA_BACKEND_URL,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=10.0
        )
        if response.status_code in (200, 201):
            print(f"[SUCCESS] Backend accepted payload (HTTP {response.status_code})")
            try:
                resp_body = response.json()
                print(f"[INFO] Backend response: {json.dumps(resp_body, indent=2)}")
            except Exception:
                print(f"[INFO] Backend response: {response.text}")
            return True
        else:
            print(f"[ERROR] Backend returned HTTP {response.status_code}: {response.text}")
            return False
    except requests.exceptions.ConnectionError:
        print(f"[WARN] Cannot connect to backend at {JAVA_BACKEND_URL}")
        print("[WARN] Backend may not be running. Payload saved locally.")
        return False
    except requests.exceptions.Timeout:
        print(f"[WARN] Request to backend timed out.")
        return False
    except Exception as e:
        print(f"[ERROR] Failed to push to backend: {e}")
        return False


# ---------------------------------------------------------------------------
# Summary Printer
# ---------------------------------------------------------------------------

def print_summary(velocity_df: pd.DataFrame) -> None:
    """Print a human-readable summary table of the analysis."""
    print("\n" + "=" * 90)
    print("  SMART INVENTORY PREDICTOR — ANALYSIS SUMMARY")
    print("=" * 90)

    critical_count = (velocity_df["alert_level"] == SEVERITY_CRITICAL).sum()
    warning_count = (velocity_df["alert_level"] == SEVERITY_WARNING).sum()
    ok_count = (velocity_df["alert_level"] == SEVERITY_OK).sum()

    print(f"\n  Period Analyzed: {int(velocity_df['days_in_data'].iloc[0])} days")
    print(f"  Items Analyzed:  {len(velocity_df)}")
    print(f"  CRITICAL Alerts: {critical_count} [RESTOCK NOW]")
    print(f"  WARNING Alerts:  {warning_count}")
    print(f"  OK:              {ok_count}")
    print("\n" + "-" * 90)

    # Sort by alert severity for display
    severity_order = {SEVERITY_CRITICAL: 0, SEVERITY_WARNING: 1, SEVERITY_OK: 2}
    display_df = velocity_df.copy()
    display_df["_sort"] = display_df["alert_level"].map(severity_order)
    display_df = display_df.sort_values("_sort").drop(columns=["_sort"])

    cols = [
        ("Item", "item_name"),
        ("Type", "item_type"),
        ("Stock", "current_stock"),
        ("V_m (u/day)", "sales_velocity_vm"),
        ("R_o (days)", "run_out_rate_days"),
        ("Pred. 7d", "predicted_7day_demand"),
        ("Alert", "alert_level"),
        ("Restock Qty", "recommended_restock_qty"),
    ]
    header = " | ".join(h for h, _ in cols)
    sep = "-" * len(header)
    print(f"  {header}")
    print(f"  {sep}")
    for _, row in display_df.iterrows():
        vals = []
        for _, col in cols:
            v = row[col]
            if isinstance(v, float):
                if col == "run_out_rate_days" and (np.isinf(v) or v == -1):
                    vals.append(f"{'∞':>10}")
                else:
                    vals.append(f"{v:>10.1f}")
            elif col == "sales_velocity_vm":
                vals.append(f"{v:>10.2f}")
            else:
                vals.append(f"{str(v):>10}")
        print("  " + " | ".join(vals))

    print("-" * 90)

    # Revenue at risk summary
    total_revenue_at_risk = velocity_df[velocity_df["alert_level"] == SEVERITY_CRITICAL]["revenue_at_risk"].sum()
    total_cost_at_risk = velocity_df[velocity_df["alert_level"] == SEVERITY_CRITICAL]["cost_of_goods_at_risk"].sum()
    print(f"\n  Financial Impact (Critical Items Only):")
    print(f"    Revenue at Risk:     ${total_revenue_at_risk:,.2f}")
    print(f"    COGS at Risk:        ${total_cost_at_risk:,.2f}")
    print(f"    Potential Lost Profit: ${total_revenue_at_risk - total_cost_at_risk:,.2f}")
    print("=" * 90 + "\n")


# ---------------------------------------------------------------------------
# Main Entry Point
# ---------------------------------------------------------------------------

def main() -> None:
    """
    Orchestrate the full analytics pipeline:
      1. Load data
      2. Compute sales velocity (Vm) and run-out rate (Ro)
      3. Classify alerts
      4. Generate visualizations
      5. Build JSON payload
      6. Push to Java backend
      7. Print summary
    """
    warnings.filterwarnings("ignore", category=FutureWarning)

    print("\n" + "#" * 90)
    print("#  Smart Inventory Predictor (SIP) — Python Analytics Engine")
    print("#" * 90 + "\n")

    ensure_directories()

    # Step 1: Load data
    print("[STEP 1/6] Loading sales transaction data...")
    df_raw = load_sales_data(DATA_PATH)

    # Step 2: Compute sales velocity
    print("[STEP 2/6] Computing Moving Average Sales Velocity (Vm)...")
    velocity_df = compute_sales_velocity(df_raw)

    # Step 3: Compute run-out rate & demand predictions
    print("[STEP 3/6] Computing Run-Out Rate (Ro) and demand predictions...")
    velocity_df = compute_run_out_rate(velocity_df)

    # Step 4: Classify alerts
    print("[STEP 4/6] Classifying alert levels...")
    velocity_df = generate_alerts(velocity_df)

    # Step 5: Generate charts
    print("[STEP 5/6] Generating visualization charts...")
    chart_files = generate_charts(velocity_df, df_raw)

    # Step 6: Build payload & push to backend
    print("[STEP 6/6] Building JSON payload and pushing to backend...")
    payload = build_alert_payload(velocity_df)
    save_payload_json(payload)

    # Cross-language bridge
    success = push_to_backend(payload)

    # Print summary
    print_summary(velocity_df)

    if chart_files:
        print(f"[INFO] Charts saved to {VISUALIZATION_DIR}")
    print("[DONE] Analytics pipeline complete.\n")

    return 0 if success else 1


if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)