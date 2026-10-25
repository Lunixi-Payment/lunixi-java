package com.lunixi.sdk.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * The request is validated locally for the fields the gateway's own validator
 * rejects, so a typo costs an error that names the key instead of a round trip
 * that reports only that something was wrong.
 */
class EvaluateRequestTest {

    @Test
    @DisplayName("Bilinmeyen alan YERELDE reddedilir ve adı söylenir")
    void rejectsAnUnknownFieldLocally() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> EvaluateRequest.create().set("tenantId", "org_1"));

        assertTrue(failure.getMessage().contains("tenantId"));
    }

    @Test
    @DisplayName("Kiracı alanı SDK yüzeyinde YOKTUR — jetondan damgalanır")
    void theTenantFieldIsAbsentFromTheSurface() {
        // Accepting it would suggest a caller may choose whose data a decision is
        // made against; the endpoint ignores the value, so offering it would only
        // mislead.
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().set("tenantId", "org_1"));
        assertFalse(EvaluateRequest.create().toMap().containsKey("tenantId"));
    }

    @Test
    @DisplayName("Motor-içi alanlar yüzeyde YOKTUR")
    void engineInternalFieldsAreAbsent() {
        for (String field : List.of("flow", "subflows", "ruleGroups", "liveSignalsMode", "basicAiMode")) {
            assertThrows(ConfigurationException.class,
                    () -> EvaluateRequest.create().set(field, true), field);
        }
    }

    @Test
    @DisplayName("Tipli kurucular wire değerlerini yazar")
    void typedSettersWriteWireValues() {
        Map<String, Object> body = EvaluateRequest.create()
                .standaloneIntegrationMode()
                .channel(FraudTaxonomy.Channel.STANDALONE_API)
                .surface(FraudTaxonomy.Surface.TRANSACTION)
                .stage(FraudTaxonomy.Stage.PRE_AUTHORIZATION)
                .operationKind(FraudTaxonomy.OperationKind.CARD_SALE)
                .authModeRequested(FraudTaxonomy.AuthMode.TWO_D)
                .authModeApplied(FraudTaxonomy.AuthMode.THREE_D)
                .productContext(FraudTaxonomy.ProductContext.DIRECT_API)
                .transactionRef("order-2026-0001")
                .subMerchantRef("sub_88")
                .payment(Map.of("amount", 25000, "currency", "TRY"))
                .toMap();

        assertEquals("standalone", body.get("integrationMode"));
        assertEquals("standalone_api", body.get("channel"));
        assertEquals("transaction", body.get("surface"));
        assertEquals("pre_authorization", body.get("stage"));
        assertEquals("card_sale", body.get("operationKind"));
        assertEquals("2d", body.get("authModeRequested"));
        assertEquals("3d", body.get("authModeApplied"));
        assertEquals("direct_api", body.get("productContext"));
        assertEquals("order-2026-0001", body.get("transactionRef"));
        assertEquals("sub_88", body.get("subMerchantRef"));
    }

    @Test
    @DisplayName("Beyan edilemeyen kanallar ve bağlamlar sözlükte YOKTUR")
    void platformOnlyValuesAreNotOffered() {
        // Declaring one of these from outside would reach flows, rules and quota
        // lanes bound to the platform's own traffic — including unbilled lanes.
        assertEquals(List.of("merchant_form", "standalone_api"), FraudTaxonomy.channelValues());
        assertEquals(
                List.of("live_session", "payment_form", "hosted_payment_form", "direct_api"),
                FraudTaxonomy.productContextValues());
        assertEquals("standalone", FraudTaxonomy.INTEGRATION_MODE_STANDALONE);
    }

    @Test
    @DisplayName("Sözlükler gateway'in kapalı listeleriyle BİREBİR")
    void dictionariesMatchTheClosedLists() {
        assertEquals(
                List.of("form_gate", "transaction", "withdrawal_gate", "settlement"),
                FraudTaxonomy.surfaceValues());
        assertEquals(
                List.of("pre_authorization", "post_authentication", "post_hoc", "async_event", "scheduled"),
                FraudTaxonomy.stageValues());
        assertEquals(
                List.of("card_sale", "card_preauth", "w2w_transfer", "w2iban_withdrawal", "bulk_payout",
                        "agent_cash_out", "bill_payment", "merchant_payment", "qr_payment", "card_topup",
                        "agent_topup", "bank_transfer_topup", "outbound_transfer", "inbound_transfer"),
                FraudTaxonomy.operationKindValues());
        assertEquals(List.of("2d", "3d"), FraudTaxonomy.authModeValues());
    }

    @Test
    @DisplayName("transactionRef ve subjectId 180 karakterle sınırlı")
    void boundsTransactionRefAndSubjectId() {
        String tooLong = "x".repeat(181);

        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().transactionRef(tooLong));
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().subjectId(tooLong));
        assertEquals(180, String.valueOf(EvaluateRequest.create()
                .transactionRef("x".repeat(180)).toMap().get("transactionRef")).length());
    }

    @Test
    @DisplayName("Boş ya da kontrol karakterli referans reddedilir")
    void rejectsBlankAndControlCharacterReferences() {
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().transactionRef("   "));
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().subjectId("order\u00011"));
    }

    @Test
    @DisplayName("subMerchantRef biçimi: harf, rakam ve . _ : @ -")
    void enforcesSubMerchantRefFormat() {
        assertEquals("a.b_c:d@e-1", EvaluateRequest.create()
                .subMerchantRef("a.b_c:d@e-1").toMap().get("subMerchantRef"));

        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().subMerchantRef("sub 88"));
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().subMerchantRef("sub/88"));
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.create().subMerchantRef(""));
        assertThrows(ConfigurationException.class,
                () -> EvaluateRequest.create().subMerchantRef("s".repeat(181)));
    }

    @Test
    @DisplayName("null değer alanı kaldırır; haritadan kurma da doğrulanır")
    void nullRemovesAndMapConstructionIsValidated() {
        Map<String, Object> body = EvaluateRequest.create()
                .transactionRef("order-1")
                .set("transactionRef", null)
                .toMap();
        assertFalse(body.containsKey("transactionRef"));

        assertEquals("pay_1", EvaluateRequest.of(Map.of("paymentId", "pay_1")).toMap().get("paymentId"));
        assertThrows(ConfigurationException.class, () -> EvaluateRequest.of(Map.of("nope", "x")));
    }

    @Test
    @DisplayName("toMap() kopya döner — isteği sonradan değiştirmek gövdeyi etkilemez")
    void toMapReturnsACopy() {
        EvaluateRequest request = EvaluateRequest.create().paymentId("pay_1");
        Map<String, Object> snapshot = request.toMap();
        request.paymentId("pay_2");

        assertEquals("pay_1", snapshot.get("paymentId"));
    }
}
