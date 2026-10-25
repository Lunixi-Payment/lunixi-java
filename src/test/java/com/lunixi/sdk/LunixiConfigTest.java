package com.lunixi.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.SignatureException;

/** Configuration is validated as it is built, so a mistake surfaces at start-up. */
class LunixiConfigTest {

    private static LunixiConfig.Builder valid() {
        return LunixiConfig.builder()
                .baseUrl("https://gateway.invalid/")
                .keyId("kid_1")
                .privateKey(com.lunixi.sdk.auth.Ed25519KeyPair.generate().privateKeyPem());
    }

    @Test
    @DisplayName("Varsayılanlar: LIVE, /api/v1/auth/token, sondaki eğik çizgi atılır")
    void appliesDefaults() {
        LunixiConfig config = valid().build();

        assertEquals("https://gateway.invalid", config.baseUrl());
        assertEquals("/api/v1/auth/token", config.authTokenPath());
        assertEquals(LunixiConfig.Environment.LIVE, config.environment());
        assertFalse(config.isTest());
        assertEquals(Duration.ofSeconds(30), config.timeout());
    }

    @Test
    @DisplayName("Eksik ya da bozuk değerler kurulumda hata verir")
    void rejectsMissingAndUnusableValues() {
        assertThrows(ConfigurationException.class, () -> valid().baseUrl(null).build());
        assertThrows(ConfigurationException.class, () -> valid().baseUrl("gateway.invalid").build());
        assertThrows(ConfigurationException.class, () -> valid().keyId("  ").build());
        assertThrows(ConfigurationException.class, () -> valid().privateKey(null).build());
        assertThrows(ConfigurationException.class, () -> valid().timeout(Duration.ZERO).build());
        assertThrows(ConfigurationException.class, () -> valid().maxRetries(-1).build());
    }

    @Test
    @DisplayName("Jeton önbellek anahtarı anahtar kimliği, adres ve ortamı birlikte kapsar")
    void tokenCacheKeyCoversKeyIdBaseUrlAndEnvironment() {
        LunixiConfig live = valid().build();
        LunixiConfig test = valid().environment(LunixiConfig.Environment.TEST).build();
        LunixiConfig otherKey = valid().keyId("kid_2").build();

        // A token is only valid for the triple that produced it; sharing a cache
        // entry across environments would hand one environment's token to the other.
        assertNotEquals(live.tokenCacheKey(), test.tokenCacheKey());
        assertNotEquals(live.tokenCacheKey(), otherKey.tokenCacheKey());
        assertEquals(live.tokenCacheKey(), valid().build().tokenCacheKey());
        assertTrue(live.tokenCacheKey().startsWith("lunixi_token_"));
    }

    @Test
    @DisplayName("Bozuk özel anahtar, ilk çağrıyı beklemeden istemci kurulurken yakalanır")
    void anUnusableKeyFailsWhenTheClientIsCreated() {
        LunixiConfig config = LunixiConfig.builder()
                .baseUrl("https://gateway.invalid")
                .keyId("kid_1")
                .privateKey("not-a-key")
                .build();

        assertThrows(SignatureException.class, () -> LunixiClient.create(config));
    }
}
