package com.analyzer.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupportedContractTypeTest {
    @Test
    void exposesOnlySevenSupportedTypes() {
        assertEquals(7, SupportedContractType.supportedValues().size());
        assertFalse(SupportedContractType.supportedValues().contains("UNKNOWN"));
        assertFalse(SupportedContractType.supportedValues().contains("UNSUPPORTED"));
    }
}
