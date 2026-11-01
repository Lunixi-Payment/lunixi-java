package com.lunixi.sdk.physicalpos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.lunixi.sdk.exception.ConfigurationException;

/** Strict declared physical POS money; counters and opaque metadata stay untouched. */
final class PhysicalPosMoney {
    private static final Set<String> FIELDS = Set.of("amountFromMinor", "amountMinor", "amountToMinor", "approvedAmountMinor", "baseMinor", "capturedMinor", "lineTotalMinor", "netBaseMinor", "netMinor", "netTaxMinor", "refundBaseMinor", "refundMinor", "refundTaxMinor", "refundedAmountMinor", "refundedMinor", "reservedAmountMinor", "reservedMinor", "reversalReservedMinor", "reversedMinor", "saleBaseMinor", "saleMinor", "saleTaxMinor", "unitAmountMinor", "unitPriceMinor", "voidBaseMinor", "voidMinor", "voidTaxMinor");
    private static final Set<String> CONTAINERS = Set.of("data", "payment", "payments", "items", "refundItems", "taxes", "rows", "transaction", "transactions", "root", "children", "itemSummary", "amountTotals", "reported", "system", "difference", "totals", "lines");
    private static final Set<String> NULLABLE = Set.of("approvedAmountMinor", "unitAmountMinor");

    private PhysicalPosMoney() { }
    static void validate(Object value) { validate(value, false); }
    private static void validate(Object value, boolean difference) {
        if (value instanceof List<?> list) { list.forEach(entry -> validate(entry, difference)); }
        if (!(value instanceof Map<?, ?> map)) return;
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) continue;
            Object amount = entry.getValue();
            if (FIELDS.contains(key)) {
                if (amount == null && NULLABLE.contains(key)) continue;
                boolean signed = difference || key.startsWith("net");
                try {
                    if (!(amount instanceof String text) || !text.matches(signed ? "0|-?[1-9][0-9]*" : "0|[1-9][0-9]*")) {
                        throw new IllegalArgumentException("Noncanonical amount.");
                    }
                    Long.parseLong(text);
                } catch (IllegalArgumentException error) {
                    throw new ConfigurationException("'" + key + "' must be a canonical minor-unit string in the signed64 range.");
                }
            } else if (CONTAINERS.contains(key)) { validate(amount, difference || "difference".equals(key)); }
        }
    }
}
