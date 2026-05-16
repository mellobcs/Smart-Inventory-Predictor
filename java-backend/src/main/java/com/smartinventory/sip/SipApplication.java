package com.smartinventory.sip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smart Inventory Predictor (SIP) — Spring Boot Application Entry Point.
 *
 * This microservice receives analytics payloads from the Python Analytics Engine
 * via REST API, persists inventory alerts to the database using Spring Data JPA,
 * and provides query endpoints for the dashboard.
 */
@SpringBootApplication
public class SipApplication {

    public static void main(String[] args) {
        SpringApplication.run(SipApplication.class, args);
    }
}