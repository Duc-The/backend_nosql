package vn.ducthe.organic.config.bootstrap;

import java.util.Locale;

public final class SeedNames {
    static final String PREFIX = "organic-muaxanh-v1-";
    static final String LEGACY_PREFIX = "organic-demo-v1-";
    static final String WELCOME_CODE = "ORGANIC10";
    private static final String[] FAMILY = {"nguyen", "tran", "le", "pham", "vo", "huynh", "dang", "bui", "do", "phan"};
    private static final String[] GIVEN = {"minhanh", "giahan", "thanhtung", "khanhlinh", "duchuy", "ngocmai"};

    private SeedNames() {}

    static String username(String key) {
        return switch (key) {
            case "warehouse" -> "tranminhphuc";
            case "sales" -> "nguyenhoaian";
            default -> {
                int number = Integer.parseInt(key.substring("customer-".length())) - 1;
                yield FAMILY[number % FAMILY.length] + GIVEN[number / FAMILY.length] + (number + 1);
            }
        };
    }

    static String email(String key) {
        return username(key) + "@muaxanh.example";
    }

    static String supplierEmail(String key) {
        return "thumua." + key.toLowerCase(Locale.ROOT) + "@muaxanh.example";
    }

    static String operationalCode(String value) {
        return value.replace("DEMO-", "MX-");
    }

    static String identifier(String value) {
        if (!value.startsWith(LEGACY_PREFIX)) {
            return value;
        }
        return PREFIX + operationalCode(value.substring(LEGACY_PREFIX.length()));
    }
}
