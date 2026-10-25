package com.lunixi.sdk.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The serialiser is part of the signing contract: the gateway re-serialises the
 * body it parsed and compares hashes, so any drift from JavaScript's own output
 * turns a correct request into an authentication failure. These cases lock the
 * output shape rather than the implementation.
 */
class JsonTest {

    @Test
    @DisplayName("Nesne boşluksuz yazılır — gateway kompakt gövdenin özetini bekler")
    void writesCompactJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", "standalone_api");
        body.put("payment", Map.of("amount", 25000));

        assertEquals("{\"channel\":\"standalone_api\",\"payment\":{\"amount\":25000}}", Json.stringify(body));
    }

    @Test
    @DisplayName("Dizi indeksi olan anahtarlar ÖNCE ve sayısal sırada gelir")
    void ordersArrayIndexKeysFirst() {
        // JavaScript iterates integer-like keys first, in numeric order. Emitting
        // insertion order here would hash differently from the gateway's
        // re-serialisation and the signed request would be rejected.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("crmId", "A");
        metadata.put("2024", "B");
        metadata.put("7", "C");
        metadata.put("note", "D");

        assertEquals("{\"7\":\"C\",\"2024\":\"B\",\"crmId\":\"A\",\"note\":\"D\"}", Json.stringify(metadata));
    }

    @Test
    @DisplayName("Kanonik olmayan sayısal anahtar sıradan ad sayılır")
    void treatsNonCanonicalNumberKeyAsName() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("01", "leading-zero");
        body.put("3", "index");

        // "01" is not the canonical decimal form of 1, so JavaScript keeps it
        // among the named keys instead of sorting it with the indices.
        assertEquals("{\"3\":\"index\",\"01\":\"leading-zero\"}", Json.stringify(body));
    }

    @Test
    @DisplayName("Eğik çizgi ve Türkçe harfler kaçışsız yazılır")
    void doesNotEscapeSlashesOrNonAscii() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantName", "Şükrü & Oğulları Ltd. Şti.");
        body.put("docsUrl", "https://example.com/tr/payments");

        assertEquals(
                "{\"merchantName\":\"Şükrü & Oğulları Ltd. Şti.\",\"docsUrl\":\"https://example.com/tr/payments\"}",
                Json.stringify(body));
    }

    @Test
    @DisplayName("Kontrol karakterleri kısa biçimle, diğerleri \\u ile kaçışlanır")
    void escapesControlCharacters() {
        assertEquals("\"a\\nb\\tc\\\"d\\\\e\"", Json.stringify("a\nb\tc\"d\\e"));
        assertEquals("\"\\u0001\"", Json.stringify("\u0001"));
    }

    @Test
    @DisplayName("Satır ayırıcıları (U+2028/U+2029) kaçışlanmaz")
    void doesNotEscapeLineSeparators() {
        // A mapper that escapes these — several do — produces a different digest
        // than the gateway computes, so a description containing one would fail
        // with an invalid-digest error and nothing visibly wrong in the body.
        assertEquals("\"a\u2028b\u2029c\"", Json.stringify("a\u2028b\u2029c"));
    }

    @Test
    @DisplayName("Tam sayı değerli ondalık, kesirsiz yazılır")
    void writesIntegralDoubleWithoutFraction() {
        assertEquals("{\"amount\":25000}", Json.stringify(Map.of("amount", 25000.0d)));
        assertEquals("{\"rate\":1.5}", Json.stringify(Map.of("rate", 1.5d)));
    }

    @Test
    @DisplayName("NaN ve sonsuzluk reddedilir")
    void rejectsNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.stringify(Map.of("x", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> Json.stringify(Map.of("x", Double.POSITIVE_INFINITY)));
    }

    @Test
    @DisplayName("Çevrilemeyen tip, ağ çağrısından ÖNCE hata verir")
    void rejectsUnsupportedType() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Json.stringify(Map.of("when", java.time.Instant.now())));
        assertTrue(failure.getMessage().contains("Instant"));
    }

    @Test
    @DisplayName("Liste ve iç içe yapı korunur")
    void writesNestedStructures() {
        assertEquals("[{\"a\":1},[true,null]]",
                Json.stringify(List.of(Map.of("a", 1), java.util.Arrays.asList(true, null))));
    }

    @Test
    @DisplayName("Okuma: nesne, dizi, sayı ve kaçışlar")
    void parsesDocuments() {
        Map<String, Object> parsed = Json.parseObject(
                "{\"status\":\"success\",\"data\":{\"score\":12.5,\"n\":7},\"rows\":[1,2],\"ok\":true,\"x\":null}");

        assertEquals("success", parsed.get("status"));
        assertEquals(List.of(1L, 2L), parsed.get("rows"));
        assertEquals(Boolean.TRUE, parsed.get("ok"));
        assertTrue(parsed.containsKey("x"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        assertEquals(12.5d, data.get("score"));
        assertEquals(7L, data.get("n"));
    }

    @Test
    @DisplayName("Okuma: wire sırası korunur")
    void preservesWireOrderOnRead() {
        Map<String, Object> parsed = Json.parseObject("{\"b\":1,\"a\":2}");
        assertEquals(List.of("b", "a"), List.copyOf(parsed.keySet()));
    }

    @Test
    @DisplayName("Okuma: boş gövde boş nesnedir, bozuk gövde hata verir")
    void handlesEmptyAndBrokenBodies() {
        assertTrue(Json.parseObject("").isEmpty());
        assertThrows(JsonParseException.class, () -> Json.parseObject("{\"a\":}"));
        assertThrows(JsonParseException.class, () -> Json.parseObject("{\"a\":1} trailing"));
    }

    @Test
    @DisplayName("Yaz-oku turu, Türkçe metni bozmaz")
    void roundTripsUnicode() {
        Map<String, Object> body = Map.of("ad", "Ömer Çağlar İşgören");
        assertEquals(body, Json.parseObject(Json.stringify(body)));
    }
}
