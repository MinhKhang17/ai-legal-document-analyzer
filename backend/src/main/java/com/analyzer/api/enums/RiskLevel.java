package com.analyzer.api.enums;

/**
 * High-level legal risk classification returned by the AI contract.
 * Keep this narrow so the BE and FE can make simple UI decisions.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
