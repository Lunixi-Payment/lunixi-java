package com.lunixi.sdk.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated token storage. Keep this independent key outside the database and web root. */
public final class SecretBox {
    private final SecretKeySpec key;
    private static final SecureRandom RANDOM = new SecureRandom();
    public SecretBox(byte[] key) {
        if (key == null || key.length != 32) { throw new IllegalArgumentException("A separate 32-byte storage key is required."); }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }
    public String encrypt(String plaintext) {
        byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + encrypted.length); System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Token encryption failed.", error); }
    }
    public String decrypt(String envelope) {
        try {
            byte[] input = Base64.getDecoder().decode(envelope);
            if (input.length < 28) { throw new IllegalArgumentException("Invalid encrypted token."); }
            return new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(input, 12)).doFinal(Arrays.copyOfRange(input, 12, input.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) { throw new IllegalStateException("Encrypted token authentication failed."); }
    }
    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode, key, new GCMParameterSpec(128, nonce)); cipher.updateAAD("lunixi-checkout-v1".getBytes(StandardCharsets.US_ASCII)); return cipher;
    }
    @Override public String toString() { return "SecretBox[[REDACTED]]"; }
}
