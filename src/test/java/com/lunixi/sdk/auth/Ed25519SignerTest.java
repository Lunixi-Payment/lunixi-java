package com.lunixi.sdk.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.exception.SignatureException;

/**
 * Signing is verified the way the gateway verifies it: the signature the SDK
 * produces is checked against the matching public key with the JVM's own
 * Ed25519 implementation. Every key in this suite is generated here — no key
 * material is committed.
 */
class Ed25519SignerTest {

    private static final String CANONICAL =
            "POST\n/api/v1/auth/token\nX-Date:2026-10-11T08:00:00Z\nX-Nonce:5f2c";

    @Test
    @DisplayName("Üretilen anahtarla imzalanan kanonik dizge, açık anahtarla DOĞRULANIR")
    void signatureVerifiesWithTheMatchingPublicKey() throws Exception {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        String signature = new Ed25519Signer(keyPair.privateKeyPem()).sign(CANONICAL);

        assertTrue(verify(keyPair.publicKeyPem(), CANONICAL, signature));
    }

    @Test
    @DisplayName("Kanonik dizgenin tek karakteri değişirse imza GEÇMEZ")
    void signatureFailsWhenTheCanonicalStringChanges() throws Exception {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        String signature = new Ed25519Signer(keyPair.privateKeyPem()).sign(CANONICAL);

        // A different nonce, a different date, a lower-case method, a stray
        // Digest line — any of them lands here, and the gateway's answer is the
        // same unhelpful 401. The contract is byte-exact.
        assertFalse(verify(keyPair.publicKeyPem(), CANONICAL.replace("5f2c", "5f2d"), signature));
    }

    @Test
    @DisplayName("Başka anahtarın imzası GEÇMEZ")
    void signatureFromAnotherKeyDoesNotVerify() throws Exception {
        Ed25519KeyPair signing = Ed25519KeyPair.generate();
        Ed25519KeyPair other = Ed25519KeyPair.generate();
        String signature = new Ed25519Signer(signing.privateKeyPem()).sign(CANONICAL);

        assertFalse(verify(other.publicKeyPem(), CANONICAL, signature));
    }

    @Test
    @DisplayName("İmza base64 ve 64 bayt")
    void signatureIsBase64OfSixtyFourBytes() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        String signature = new Ed25519Signer(keyPair.privateKeyPem()).sign(CANONICAL);

        assertEquals(64, Base64.getDecoder().decode(signature).length);
    }

    @Test
    @DisplayName("Aynı anahtar ve dizge için imza belirlenimli — Ed25519 nonce taşımaz")
    void signingIsDeterministic() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        Ed25519Signer signer = new Ed25519Signer(keyPair.privateKeyPem());

        assertEquals(signer.sign(CANONICAL), signer.sign(CANONICAL));
    }

    @Test
    @DisplayName("PEM zırhı olmayan base64 anahtar da kabul edilir")
    void acceptsBase64KeyWithoutPemArmour() throws Exception {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        String bare = keyPair.privateKeyPem()
                .replaceAll("-----[A-Z ]+-----", "")
                .replaceAll("\\s+", "");

        String signature = new Ed25519Signer(bare).sign(CANONICAL);
        assertTrue(verify(keyPair.publicKeyPem(), CANONICAL, signature));
    }

    @Test
    @DisplayName("Çıplak 32 baytlık tohum anahtar da kabul edilir")
    void acceptsRawThirtyTwoByteSeed() throws Exception {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
        byte[] pkcs8 = Base64.getDecoder().decode(keyPair.privateKeyPem()
                .replaceAll("-----[A-Z ]+-----", "")
                .replaceAll("\\s+", ""));
        byte[] seed = java.util.Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length);

        String signature = new Ed25519Signer(Base64.getEncoder().encodeToString(seed)).sign(CANONICAL);
        assertTrue(verify(keyPair.publicKeyPem(), CANONICAL, signature));
    }

    @Test
    @DisplayName("Bozuk veya boş anahtar, ilk çağrıyı beklemeden hata verir")
    void rejectsUnusableKeyMaterial() {
        assertThrows(SignatureException.class, () -> new Ed25519Signer(""));
        assertThrows(SignatureException.class, () -> new Ed25519Signer("not-a-key"));
        assertThrows(SignatureException.class,
                () -> new Ed25519Signer("-----BEGIN PRIVATE KEY-----\n!!!\n-----END PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("Üretilen çift, PEM zırhı taşır ve özel anahtar şifrelenmemiştir")
    void generatedPairIsPemArmoured() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.generate();

        assertTrue(keyPair.privateKeyPem().startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(keyPair.privateKeyPem().trim().endsWith("-----END PRIVATE KEY-----"));
        assertTrue(keyPair.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"));
        assertFalse(keyPair.privateKeyPem().contains("ENCRYPTED"));
    }

    private static boolean verify(String publicKeyPem, String message, String base64Signature) throws Exception {
        PublicKey publicKey = Ed25519Signer.publicKeyFrom(publicKeyPem);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(message.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(base64Signature));
    }
}
