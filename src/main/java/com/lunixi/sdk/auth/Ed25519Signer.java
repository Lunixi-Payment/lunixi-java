package com.lunixi.sdk.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import com.lunixi.sdk.exception.SignatureException;

/**
 * Signs the canonical request string with the account's Ed25519 private key.
 *
 * <p>The signature is plain Ed25519 over the UTF-8 bytes of the canonical
 * string, base64-encoded into the {@code X-Signature} header; the gateway
 * verifies it with the public key registered for the key id. Everything is JDK
 * built-in — {@code Ed25519} is available from Java 15 on, so the SDK needs no
 * cryptography dependency.
 *
 * <p>The accepted key form is the unencrypted PKCS#8 PEM issued for the
 * account. A raw 32-byte seed, a 64-byte libsodium secret key, and base64 of
 * either are accepted too, so a key stored in an environment variable does not
 * have to be re-wrapped by hand.
 *
 * <p>Instances are immutable and safe to share between threads; each
 * {@link #sign(String)} call uses its own {@link Signature} object.
 */
public final class Ed25519Signer {

    /** Unencrypted PKCS#8 Ed25519 DER prefix (RFC 8410): 16 bytes, then the 32-byte seed. */
    private static final byte[] PKCS8_PREFIX = hex("302e020100300506032b657004220420");

    /** SubjectPublicKeyInfo DER prefix for an Ed25519 public key: 12 bytes, then the 32-byte key. */
    private static final byte[] SPKI_PREFIX = hex("302a300506032b6570032100");

    private static final int SEED_LENGTH = 32;

    private final PrivateKey privateKey;

    /**
     * @param privateKeyMaterial the unencrypted PKCS#8 Ed25519 PEM issued for the
     *                           account, or raw/base64 key material
     * @throws SignatureException when the key cannot be read
     */
    public Ed25519Signer(String privateKeyMaterial) {
        this.privateKey = privateKeyFrom(privateKeyMaterial);
    }

    /**
     * Signs the canonical string and returns the {@code X-Signature} header value.
     *
     * @param canonicalString the string produced by
     *                        {@link CanonicalRequest#build(String, String, String, String, String)}
     * @return the base64-encoded Ed25519 signature
     * @throws SignatureException when signing fails
     */
    public String sign(String canonicalString) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(canonicalString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new SignatureException("Ed25519 imzası üretilemedi / Ed25519 signing failed.", failure);
        }
    }

    // ───────────────────────────── key handling ─────────────────────────────

    /**
     * Reads an Ed25519 private key from a PEM, or from raw/base64 key material.
     *
     * @param material the key text
     * @return the private key
     * @throws SignatureException when the material is not a usable Ed25519 key
     */
    public static PrivateKey privateKeyFrom(String material) {
        byte[] bytes = decodeKeyMaterial(material);
        byte[] pkcs8 = bytes.length == SEED_LENGTH ? wrapSeed(bytes)
                : bytes.length == 64 ? wrapSeed(Arrays.copyOf(bytes, SEED_LENGTH))
                : bytes;
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException failure) {
            throw new SignatureException(
                    "Ed25519 özel anahtarı okunamadı; hesabınız için verilen şifrelenmemiş PKCS#8 PEM bekleniyor. "
                            + "/ Could not read the Ed25519 private key; the unencrypted PKCS#8 PEM issued for your "
                            + "account is expected.", failure);
        }
    }

    /**
     * Reads an Ed25519 public key from a SubjectPublicKeyInfo PEM, or from raw
     * 32-byte / base64 key material. Useful for checking a key pair before it is
     * registered, and for verifying a signature in your own tests.
     *
     * @param material the key text
     * @return the public key
     * @throws SignatureException when the material is not a usable Ed25519 key
     */
    public static PublicKey publicKeyFrom(String material) {
        byte[] bytes = decodeKeyMaterial(material);
        byte[] spki = bytes.length == SEED_LENGTH ? concat(SPKI_PREFIX, bytes) : bytes;
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (GeneralSecurityException failure) {
            throw new SignatureException(
                    "Ed25519 açık anahtarı okunamadı / could not read the Ed25519 public key.", failure);
        }
    }

    /** Wraps a PEM body in the armour lines, 64 characters per line. */
    static String toPem(String label, byte[] der) {
        String body = Base64.getEncoder().encodeToString(der);
        StringBuilder pem = new StringBuilder(body.length() + 64)
                .append("-----BEGIN ").append(label).append("-----\n");
        for (int offset = 0; offset < body.length(); offset += 64) {
            pem.append(body, offset, Math.min(offset + 64, body.length())).append('\n');
        }
        return pem.append("-----END ").append(label).append("-----\n").toString();
    }

    private static byte[] wrapSeed(byte[] seed) {
        return concat(PKCS8_PREFIX, seed);
    }

    private static byte[] concat(byte[] prefix, byte[] tail) {
        byte[] out = Arrays.copyOf(prefix, prefix.length + tail.length);
        System.arraycopy(tail, 0, out, prefix.length, tail.length);
        return out;
    }

    private static byte[] decodeKeyMaterial(String input) {
        if (input == null || input.isBlank()) {
            throw new SignatureException("Anahtar boş / the key is empty.");
        }
        String trimmed = input.trim();
        if (trimmed.contains("-----BEGIN")) {
            String body = trimmed.replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s+", "");
            return decodeBase64(body, "PEM gövdesi geçerli base64 değil / the PEM body is not valid base64.");
        }
        return decodeBase64(trimmed, "Anahtar geçerli base64 değil / the key is not valid base64.");
    }

    private static byte[] decodeBase64(String value, String failureMessage) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException invalid) {
            throw new SignatureException(failureMessage, invalid);
        }
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
