package com.analyzer.api.enums;

import java.util.Arrays;
import java.util.List;

public enum SupportedContractType {
    RENTAL("Hợp đồng thuê phòng/nhà"),
    PART_TIME_EMPLOYMENT("Hợp đồng lao động bán thời gian"),
    INTERNSHIP("Hợp đồng thực tập"),
    COLLABORATOR("Hợp đồng cộng tác viên"),
    FREELANCE_SERVICE("Hợp đồng dịch vụ/freelance quy mô nhỏ"),
    SMALL_ASSET_SALE("Hợp đồng mua bán tài sản cá nhân nhỏ"),
    PERSONAL_LOAN("Hợp đồng vay cá nhân đơn giản"),
    UNSUPPORTED("Không được hỗ trợ"),
    UNKNOWN("Chưa xác định");

    private final String displayName;

    SupportedContractType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSupported() {
        return this != UNSUPPORTED && this != UNKNOWN;
    }

    public static SupportedContractType requireSupported(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CONTRACT_TYPE_CONFIRMATION_REQUIRED");
        }
        try {
            SupportedContractType type = valueOf(value.trim().toUpperCase());
            if (!type.isSupported()) {
                throw new IllegalArgumentException("UNSUPPORTED_CONTRACT_TYPE: " + supportedValues());
            }
            return type;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("UNSUPPORTED_CONTRACT_TYPE")) {
                throw exception;
            }
            throw new IllegalArgumentException("UNSUPPORTED_CONTRACT_TYPE: " + supportedValues());
        }
    }

    public static List<String> supportedValues() {
        return Arrays.stream(values()).filter(SupportedContractType::isSupported).map(Enum::name).toList();
    }
}
