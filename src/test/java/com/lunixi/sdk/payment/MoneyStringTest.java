package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MoneyStringTest {
    @Test void canonicalStringsAndCurrencyExponentsRemainExact() {
        String large = "9007199254740993";
        assertEquals(large, Money.minor(large, "JPY"));
        assertEquals(large, Money.minor("90071992547409.93", "TRY"));
        assertEquals(large, Money.minor("9007199254740.993", "KWD"));
        for (Object invalid : new Object[]{1, 1.5, "-0", "01", "+1", " 1", "1.0", "1e3", "9223372036854775808"}) {
            assertThrows(IllegalArgumentException.class, () -> Money.integer(invalid));
        }
        assertEquals(9007199254740993L, Money.integer(large));
        assertDoesNotThrow(() -> Money.validateFields(Map.of("balanceMinor", "-9007199254740993"), true));
        assertThrows(IllegalArgumentException.class, () -> Money.validateFields(Map.of("data", Map.of("amount", 1)), true));
        assertDoesNotThrow(() -> Money.validateFields(Map.of("amount", large, "metadata", Map.of("amount", 1), "providerPayload", Map.of("amount", 1), "rate", 1.5), false));
        assertThrows(IllegalArgumentException.class, () -> Money.minor("1", "ZZZ"));
    }
}
