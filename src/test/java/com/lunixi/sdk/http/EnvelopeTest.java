package com.lunixi.sdk.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.json.Json;

/** The envelope's traps, each one locked by a case. */
class EnvelopeTest {

    @Test
    @DisplayName("Zarfta 'success' alanı YOKTUR — sonuç 'status' metninde taşınır")
    void outcomeTravelsInStatusNotInABooleanSuccess() {
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"decision\":\"allow\"},"
                        + "\"statusCode\":200,\"requestId\":\"req_1\"}");

        // A client reading a boolean `success` sees nothing at all here. Depending
        // on the language that absence reads as "failed" (breaking every call) or
        // as "no error" (hiding every failure), so the SDK exposes only `status`.
        assertFalse(response.containsKey("success"));
        assertEquals("success", Envelope.status(response).orElseThrow());
        assertFalse(Envelope.isFailure(response));
        assertEquals("SUCCESS", Envelope.code(response).orElseThrow());
        assertEquals("req_1", Envelope.requestId(response).orElseThrow());
        assertEquals("allow", Envelope.data(response).get("decision"));
    }

    @Test
    @DisplayName("200 yanıt 'failure' taşıyabilir — durum kodu tek başına yetmez")
    void aTwoHundredCanReportAFailure() {
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"failure\",\"code\":\"fraud.quota_exhausted\",\"message\":\"…\","
                        + "\"data\":null,\"statusCode\":200}");

        assertTrue(Envelope.isFailure(response));
        assertEquals("fraud.quota_exhausted", Envelope.code(response).orElseThrow());
    }

    @Test
    @DisplayName("'partial' hata DEĞİLDİR — kısmi başarı yükte raporlanır")
    void partialIsNotAFailure() {
        // A batch that wrote some of its events reports `partial`; treating it as
        // a failure would throw away the events that did land.
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"partial\",\"code\":\"partial\",\"data\":{\"total\":2,\"inserted\":1,\"failed\":1}}");

        assertFalse(Envelope.isFailure(response));
        assertEquals("partial", Envelope.status(response).orElseThrow());
    }

    @Test
    @DisplayName("pageInfo, data'nın KARDEŞİDİR — data çıplak dizi olsa bile okunur")
    void pageInfoIsASiblingOfData() {
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"success\",\"data\":[{\"traceId\":\"t1\"},{\"traceId\":\"t2\"}],"
                        + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"cur_2\",\"totalCount\":42}}");

        assertEquals(2, Envelope.rows(response).size());
        assertEquals("t1", Envelope.rows(response).get(0).get("traceId"));
        assertTrue(Envelope.hasMore(response));
        assertEquals("cur_2", Envelope.nextCursor(response).orElseThrow());
        assertEquals(42L, Envelope.totalCount(response).orElseThrow());
    }

    @Test
    @DisplayName("pageInfo yükün içinde de olabilir")
    void pageInfoIsAlsoReadFromInsideThePayload() {
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"success\",\"data\":{\"items\":[],\"pageInfo\":{\"nextCursor\":\"cur_9\"}}}");

        assertEquals("cur_9", Envelope.nextCursor(response).orElseThrow());
    }

    @Test
    @DisplayName("Son sayfada imleç yok, toplam sayı varsayılan olarak yok")
    void lastPageCarriesNoCursorAndUsuallyNoTotal() {
        Map<String, Object> response = Json.parseObject(
                "{\"status\":\"success\",\"data\":[],\"pageInfo\":{\"hasMore\":false,\"nextCursor\":null}}");

        assertTrue(Envelope.nextCursor(response).isEmpty());
        assertFalse(Envelope.hasMore(response));
        // Cursor paging does not count by default; an SDK that reported 0 here
        // would be inventing a total the server never produced.
        assertTrue(Envelope.totalCount(response).isEmpty());
    }

    @Test
    @DisplayName("Beklenen şekil gelmezse boş değer döner, istisna atılmaz")
    void readsOddShapesWithoutThrowing() {
        assertTrue(Envelope.data(Json.parseObject("{\"data\":[]}")).isEmpty());
        assertTrue(Envelope.rows(Json.parseObject("{\"data\":{}}")).isEmpty());
        assertTrue(Envelope.status(Map.of()).isEmpty());
        assertTrue(Envelope.pageInfo(null).isEmpty());
    }
}
