package com.lunixi.sdk.physicalpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lunixi.sdk.internal.ApiClient;

class PhysicalPosResponseImmutabilityTest {

    @Test
    void exchangeCopiesNestedInputAndRejectsMutationWithoutLosingNulls() {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("amountMinor", "9007199254740993");
        List<Object> history = new ArrayList<>(List.of(payment));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("history", history);
        source.put("message", null);

        ApiClient.Exchange exchange = new ApiClient.Exchange(202, source);
        payment.put("amountMinor", "1");
        history.clear();
        source.clear();

        assertEquals(202, exchange.statusCode());
        assertNull(exchange.envelope().get("message"));
        List<?> copiedHistory = (List<?>) exchange.envelope().get("history");
        Map<?, ?> copiedPayment = (Map<?, ?>) copiedHistory.get(0);
        assertEquals("9007199254740993", copiedPayment.get("amountMinor"));
        assertThrows(UnsupportedOperationException.class, copiedPayment::clear);
        assertThrows(UnsupportedOperationException.class, copiedHistory::clear);
        assertThrows(UnsupportedOperationException.class, () -> exchange.envelope().clear());
    }

    @Test
    void writeResultOwnsItsNestedPaymentSnapshot() {
        Map<String, Object> payment = new LinkedHashMap<>(Map.of("amountMinor", "1000"));
        Map<String, Object> source = new LinkedHashMap<>(Map.of("payment", payment, "replayed", false));
        PhysicalPosWriteResult result = new PhysicalPosWriteResult("payment", source, 202, "operation-1");
        payment.put("amountMinor", "1");
        source.clear();

        assertEquals("1000", result.payment().get("amountMinor"));
        assertThrows(UnsupportedOperationException.class, () -> result.data().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.payment().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.entity().clear());
    }

    @Test
    void webhookDataAndRawEnvelopeRetainIndependentReadOnlySnapshots() {
        Map<String, Object> payment = new LinkedHashMap<>(Map.of("amountMinor", "1000"));
        Map<String, Object> envelope = new LinkedHashMap<>(Map.of("data", payment));
        PhysicalPosWebhookEvent event = new PhysicalPosWebhookEvent("event-1", "physical_pos.payment.approved",
                "payment", "TEST", null, 1L, payment, envelope);
        payment.clear();
        envelope.clear();

        assertEquals("1000", event.data().get("amountMinor"));
        Map<?, ?> nested = (Map<?, ?>) event.raw().get("data");
        assertEquals("1000", nested.get("amountMinor"));
        assertThrows(UnsupportedOperationException.class, nested::clear);
        assertThrows(UnsupportedOperationException.class, () -> event.data().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.raw().clear());
    }
}
