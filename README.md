# Smart Inventory Predictor (SIP)

A **Full-Stack Enterprise Integration Project** linking a Python Data Science Analytics Engine to a Java Spring Boot REST Backend for real-time inventory risk prediction.

---

## 📋 Table of Contents
- [The Problem](#-the-problem)
- [System Architecture](#-system-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [How It Works (Step-by-Step)](#-how-it-works-step-by-step)
- [API Reference](#-api-reference)
- [Running Tests](#-running-tests)
- [Postman Testing](#-postman-testing)
- [Production Deployment](#-production-deployment)
- [Data Flow Walkthrough](#-data-flow-walkthrough)

---

## 🔥 The Problem

Small-to-medium e-commerce platforms lose significant revenue due to two major inventory issues:

| Problem | Impact |
|---------|--------|
| **Stockouts** 🚫 | High-demand items sell out → missed revenue. Restocking takes days. |
| **Overstocking** 💰 | Capital tied up in slow-moving inventory due to predictive guesswork. |

**The Solution:** An automated system that treats inventory as a live data stream. A **Python engine** processes historical sales velocity to determine risk metrics, while a **Java Spring Boot microservice** handles the business logic, providing immediate, actionable data to the platform administrator.

---

## 🏗 System Architecture

```
┌─────────────────────────┐    REST API (JSON)     ┌──────────────────────────┐
│   Python Analytics      │ ──────────────────────> │  Java Spring Boot REST  │
│   Engine                │                        │  Backend Application     │
│   (Pandas, NumPy,       │                        │  (Spring Boot 3.x)      │
│    SciPy, Matplotlib)   │                        │                          │
└─────────────────────────┘                        └──────────────────────────┘
          ▲                                                   │
          │ Ingests                                           │ Persists
          ▼                                                   ▼
┌─────────────────────────┐                        ┌──────────────────────────┐
│  Thrift'd Store Sales    │                        │    Relational DB         │
│  Data (CSV / MySQL)      │                        │  (H2 / MySQL)            │
└─────────────────────────┘                        └──────────────────────────┘
```

### Data Flow:
1. **Python Engine** reads sales transaction CSV → computes `Vm` (Sales Velocity) + `Ro` (Run-Out Rate)
2. **Python Engine** packages results as JSON → POSTs to Java backend via HTTP
3. **Java Backend** validates payload → filters false positives → persists to database
4. **Java Backend** exposes REST endpoints for querying alerts and dashboard metrics

---

## 🛠 Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Analytics Engine** | Python 3.11, Pandas, NumPy, SciPy | Data ingestion & velocity computation |
| **Visualization** | Matplotlib, Seaborn | Chart generation (sales velocity, stock vs demand) |
| **Cross-Language Bridge** | Python `requests` → HTTP POST | Transmits JSON payload to Java backend |
| **Backend API** | Java 17+, Spring Boot 3.x | RESTful service layer |
| **ORM** | Spring Data JPA | Database persistence |
| **Database (Dev)** | H2 (In-Memory) | Rapid development & testing |
| **Database (Prod)** | MySQL | Production relational storage |
| **API Testing** | Postman | Endpoint verification |

---

## 📁 Project Structure

```
Smart Inventory system/
├── python-engine/                  # Python Analytics Engine
│   ├── analytics_engine.py         # Main engine script
│   ├── data/
│   │   └── sample_sales.csv       # Sample transaction data
│   ├── output/                     # Generated reports & charts (auto-created)
│   │   ├── analytics_report.json  # JSON payload output
│   │   └── charts/                # Visualization PNGs
│   └── requirements.txt           # Python dependencies
│
├── java-backend/                   # Java Spring Boot Backend
│   ├── pom.xml                    # Maven project configuration
│   └── src/
│       ├── main/
│       │   ├── java/com/smartinventory/sip/
│       │   │   ├── SipApplication.java            # Entry point
│       │   │   ├── controller/
│       │   │   │   └── InventoryAlertController.java  # REST endpoints
│       │   │   ├── dto/
│       │   │   │   ├── AlertPayload.java          # Root payload DTO
│       │   │   │   └── InventoryAlertDto.java     # Single alert DTO
│       │   │   ├── entity/
│       │   │   │   └── InventoryAlert.java        # JPA entity
│       │   │   ├── exception/
│       │   │   │   ├── GlobalExceptionHandler.java # Centralized error handling
│       │   │   │   └── ResourceNotFoundException.java
│       │   │   ├── repository/
│       │   │   │   └── InventoryAlertRepository.java  # Spring Data JPA
│       │   │   └── service/
│       │   │       └── InventoryAlertService.java # Business logic
│       │   └── resources/
│       │       └── application.yml                # Configuration
│       └── test/
│           └── java/com/smartinventory/sip/
│               └── SipApplicationTests.java
│
├── postman/                        # Postman collections
│   └── SIP_API.postman_collection.json
│
└── README.md                       # This file
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Check Installation |
|------|---------|-------------------|
| Python | 3.11+ | `python --version` |
| Java | 17+ | `java --version` |
| Maven | 3.8+ | `mvn --version` |
| Node.js | 18+ (optional, for live demo) | `node --version` |

### 1️⃣ Python Engine Setup

```bash
# Navigate to project root
cd Smart Inventory system

# Install Python dependencies
pip install -r python-engine/requirements.txt

# Run the analytics engine
python python-engine/analytics_engine.py
```

Expected output:
- Terminal: Full analysis summary with Vm, Ro, and alert classifications
- `python-engine/output/analytics_report.json` — JSON payload
- `python-engine/output/charts/` — Visualization PNGs

### 2️⃣ Java Backend Setup

```bash
# Navigate to java-backend
cd java-backend

# Build the project
mvn clean package -DskipTests

# Run with H2 (development)
mvn spring-boot:run

# Or run with MySQL (production profile)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

The backend starts on **http://localhost:8080**.

### 3️⃣ Connect Engine → Backend

```bash
# In a new terminal, run the Python engine
# (It auto-pushes to http://localhost:8080/api/inventory/alerts)
python python-engine/analytics_engine.py
```

---

## 🔬 How It Works (Step-by-Step)

### Step 1: Data Pipeline & Processing (Python)

The Python engine ingests `sample_sales.csv` and performs:

1. **Group by item** → aggregate daily sales per item type/name
2. **Compute Vm** (Moving Average Sales Velocity):
   ```
   Vm = total_units_sold / number_of_days
   ```
3. **Compute Ro** (Run-Out Rate):
   ```
   Ro = current_stock / Vm  (days until stockout)
   ```
4. **Predict 7-day demand**:
   ```
   Predicted_Demand = Vm × 7
   ```
5. **Threshold check**: If `stock_on_hand < Predicted_Demand` → **CRITICAL_RESTOCK_WARN**

### Step 2: Cross-Language Bridge (REST API)

The Python script packages results into JSON and POSTs to:
```
POST http://localhost:8080/api/inventory/alerts
Content-Type: application/json
```

### Step 3: Enterprise Logic & Storage (Java Spring Boot)

1. **DTO Validation** — Incoming payload validated with Jakarta Validation annotations
2. **False-Positive Filtering** — Rejects items with zero velocity or no actual risk
3. **JPA Persistence** — Saves validated alerts to `inventory_alerts` table
4. **System Alert Output** — Formatted dashboard-ready alert box in logs

---

## 📡 API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| **POST** | `/api/inventory/alerts` | Receive analytics payload from Python engine |
| **GET** | `/api/inventory/alerts` | List all persisted alerts |
| **GET** | `/api/inventory/alerts/{id}` | Get alert by ID |
| **GET** | `/api/inventory/alerts/level/{level}` | Filter by severity (`CRITICAL_RESTOCK_WARN`, `OVERSTOCK_WARN`, `OK`) |
| **GET** | `/api/inventory/alerts/recent` | Get 10 most recent alerts |
| **GET** | `/api/inventory/alerts/summary` | Aggregate summary statistics |
| **DELETE** | `/api/inventory/alerts/{id}` | Delete an alert |

### Example POST Payload

```json
{
  "source": "SmartInventoryPredictor-PythonEngine",
  "generatedAt": "2026-05-16T10:30:00",
  "periodDays": 7,
  "totalItemsAnalyzed": 6,
  "criticalAlerts": 2,
  "warningAlerts": 0,
  "alerts": [
    {
      "itemType": "Hoodie",
      "itemName": "Oversized Hoodie - Black",
      "currentStock": 12,
      "salesVelocityVm": 52.57,
      "runOutRateDays": 0.2,
      "predicted7DayDemand": 368.0,
      "revenueAtRisk": 22074.73,
      "recommendedRestockQty": 356,
      "alertLevel": "CRITICAL_RESTOCK_WARN",
      "profitMarginPct": 58.3,
      "generatedAt": "2026-05-16T10:30:00"
    }
  ]
}
```

---

## 🧪 Running Tests

### Python Engine (Manual verification)
```bash
cd python-engine
python analytics_engine.py
```
The engine outputs a complete summary table and generates charts in `output/charts/`.

### Java Backend Tests
```bash
cd java-backend
mvn test
```

---

## 📮 Postman Testing

A Postman collection is included at `postman/SIP_API.postman_collection.json`.

### Import Instructions:
1. Open Postman → **File** → **Import**
2. Select `postman/SIP_API.postman_collection.json`
3. The collection includes pre-configured requests for all endpoints

### Quick Test Without Java Backend:
Use the included **"POST Simulate Python Engine"** request — it contains the complete sample payload.

---

## 🏭 Production Deployment

### Database Migration: H2 → MySQL

1. Create the MySQL database:
   ```sql
   CREATE DATABASE smart_inventory;
   ```

2. Run the backend with the `prod` profile:
   ```bash
   cd java-backend
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

3. Set the MySQL password via environment variable:
   ```bash
   export MYSQL_PASSWORD=your_secure_password
   ```

### Building for Production
```bash
cd java-backend
mvn clean package -DskipTests
java -jar target/sip-backend-1.0.0.jar --spring.profiles.active=prod
```

---

## 💡 Data Flow Walkthrough

### Scenario: Oversized Hoodie Stockout Risk

1. **Raw Data**: 7 days of sales show 368 hoodies sold (avg ~52.6/day)
2. **Current Stock**: Only 12 hoodies remaining
3. **Engine Computes**:
   - Vm = 368 ÷ 7 = **52.57 units/day**
   - Ro = 12 ÷ 52.57 = **0.2 days** (stockout in ~5 hours!)
   - 7-day predicted demand = 52.57 × 7 = **368 units**
   - Alert: `CRITICAL_RESTOCK_WARN`
4. **Backend Persists**: Alert saved with recommended restock of **356 units**
5. **Dashboard Ready**: Admin sees "Oversized Hoodie - Black" needs immediate restock with $22,074.73 revenue at risk

---

## 📊 Mathematical Models

| Metric | Formula | Description |
|--------|---------|-------------|
| **Vm** | `∑units_sold ÷ days` | Moving Average Sales Velocity (units/day) |
| **Ro** | `current_stock ÷ Vm` | Run-Out Rate (days until stock = 0) |
| **7d Demand** | `Vm × 7` | Predicted units needed for next week |
| **Revenue at Risk** | `7d_Demand × unit_price` | Revenue impact of stockout |
| **Critical Threshold** | `stock < 7d_Demand` | Triggers CRITICAL_RESTOCK_WARN |

---

## 📝 License

This project is for educational/demonstration purposes as part of a full-stack enterprise integration portfolio.

---

Built with ❤️ using Python, Java, Spring Boot, and a lot of data.
