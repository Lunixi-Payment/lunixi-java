package com.lunixi.sdk.auth;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import com.lunixi.sdk.exception.SignatureException;

/**
 * A freshly generated Ed25519 key pair, in the PEM forms the platform works
 * with: an unencrypted PKCS#8 private key and a SubjectPublicKeyInfo public
 * key.
 *
 * <p>Generating the pair yourself is the form to prefer: you upload only
 * {@link #publicKeyPem()} and the private key never leaves your systems.
 *
 * <p>Store {@link #privateKeyPem()} the way you store any other secret — a
 * secret manager or an environment variable, never a source file and never a
 * log line. A key that leaks can sign requests as your account until it is
 * revoked.
 */
public final class Ed25519KeyPair {

    private final String privateKeyPem;
    private final String publicKeyPem;

    private Ed25519KeyPair(String privateKeyPem, String publicKeyPem) {
        this.privateKeyPem = privateKeyPem;
        this.publicKeyPem = publicKeyPem;
    }

    /**
     * Generates a new key pair.
     *
     * @return the generated pair
     * @throws SignatureException when the JVM cannot generate Ed25519 keys
     */
    public static Ed25519KeyPair generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new Ed25519KeyPair(
                    Ed25519Signer.toPem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    Ed25519Signer.toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (GeneralSecurityException failure) {
            throw new SignatureException(
                    "Ed25519 anahtar çifti üretilemedi; JVM'in Ed25519 desteği gerekir (Java 15+). "
                            + "/ Could not generate an Ed25519 key pair; the JVM must support Ed25519 (Java 15+).",
                    failure);
        }
    }

    /** The private key as an unencrypted PKCS#8 PEM. Keep it secret. */
    public String privateKeyPem() {
        return privateKeyPem;
    }

    /** The public key as a SubjectPublicKeyInfo PEM. This is the half you upload. */
    public String publicKeyPem() {
        return publicKeyPem;
    }
}
