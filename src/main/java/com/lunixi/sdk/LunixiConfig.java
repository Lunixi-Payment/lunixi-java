package com.lunixi.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import com.lunixi.sdk.auth.TokenStore;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.http.JdkHttpTransport;

/**
 * Immutable SDK configuration: where to call, which key to sign with, and how
 * the transport behaves. Validated as it is built, so a misconfiguration
 * surfaces at start-up rather than on the first live call.
 *
 * <pre>
 * LunixiConfig config = LunixiConfig.builder()
 *         .baseUrl("https://api.example.com")
 *         .keyId(System.getenv("LUNIXI_KEY_ID"))
 *         .privateKey(System.getenv("LUNIXI_PRIVATE_KEY"))
 *         .environment(LunixiConfig.Environment.TEST)
 *         .build();
 * </pre>
 */
public final class LunixiConfig {

    /** Which key set the configuration is for. */
    public enum Environment {
        /** Live keys and live money. */
        LIVE,
        /** Test keys. The same hosts, with keys that do not move real money. */
        TEST
    }

    private static final String DEFAULT_AUTH_TOKEN_PATH = "/api/v1/auth/token";

    private final String baseUrl;
    private final String keyId;
    private final String privateKey;
    private final Environment environment;
    private final String authTokenPath;
    private final Duration timeout;
    private final int maxRetries;
    private final String userAgent;
    private final HttpTransport transport;
    private final TokenStore tokenStore;

    private LunixiConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.keyId = builder.keyId;
        this.privateKey = builder.privateKey;
        this.environment = builder.environment;
        this.authTokenPath = builder.authTokenPath;
        this.timeout = builder.timeout;
        this.maxRetries = builder.maxRetries;
        this.userAgent = builder.userAgent;
        this.transport = builder.transport;
        this.tokenStore = builder.tokenStore;
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String keyId() {
        return keyId;
    }

    public String privateKey() {
        return privateKey;
    }

    public Environment environment() {
        return environment;
    }

    public boolean isTest() {
        return environment == Environment.TEST;
    }

    public String authTokenPath() {
        return authTokenPath;
    }

    public Duration timeout() {
        return timeout;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public String userAgent() {
        return userAgent;
    }

    public HttpTransport transport() {
        return transport;
    }

    public TokenStore tokenStore() {
        return tokenStore;
    }

    /**
     * A stable cache key for the access token, derived from the key id, the base
     * URL and the environment.
     *
     * <p>All three are in the key because a token is only valid for the triple
     * that produced it: an application talking to two environments with two
     * keys must not hand one environment's token to the other.
     *
     * @return the cache key
     */
    public String tokenCacheKey() {
        String material = keyId + '|' + baseUrl + '|' + environment.name();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return "lunixi_token_" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException absent) {
            throw new ConfigurationException("SHA-256 bu JVM'de yok / SHA-256 is unavailable on this JVM.");
        }
    }

    /** Builder for {@link LunixiConfig}. */
    public static final class Builder {

        private String baseUrl;
        private String keyId;
        private String privateKey;
        private Environment environment = Environment.LIVE;
        private String authTokenPath = DEFAULT_AUTH_TOKEN_PATH;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private String userAgent = "lunixi-java-sdk";
        private HttpTransport transport;
        private TokenStore tokenStore;

        private Builder() {
        }

        /**
         * @param baseUrl the gateway base URL, scheme included, without a trailing path
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param keyId the key id issued for your account, sent as {@code X-Key-Id}
         * @return this builder
         */
        public Builder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        /**
         * @param privateKey the unencrypted PKCS#8 Ed25519 private key PEM. Read it
         *                   from a secret manager or an environment variable; never
         *                   from a source file.
         * @return this builder
         */
        public Builder privateKey(String privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * @param environment which key set this configuration is for
         * @return this builder
         */
        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        /**
         * @param authTokenPath the token endpoint path, when it has been moved for
         *                      your account
         * @return this builder
         */
        public Builder authTokenPath(String authTokenPath) {
            this.authTokenPath = authTokenPath;
            return this;
        }

        /**
         * @param timeout the per-request timeout
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param maxRetries how many times a <i>safe</i> request may be retried.
         *                   Only reads and calls carrying an idempotency key are
         *                   ever retried, so this never risks a double submit.
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * @param userAgent the {@code User-Agent} to send; adding your own
         *                  application's name makes support requests easier to trace
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * @param transport the HTTP transport to use instead of the default
         * @return this builder
         */
        public Builder transport(HttpTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * @param tokenStore where to cache the access token. Supply a shared store
         *                   when several processes or instances serve the same
         *                   account, so they do not each fetch their own token.
         * @return this builder
         */
        public Builder tokenStore(TokenStore tokenStore) {
            this.tokenStore = tokenStore;
            return this;
        }

        /**
         * @return the validated configuration
         * @throws ConfigurationException when a required value is missing or unusable
         */
        public LunixiConfig build() {
            baseUrl = requireText(baseUrl, "baseUrl");
            keyId = requireText(keyId, "keyId");
            privateKey = requireText(privateKey, "privateKey");

            baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            java.net.URI origin;
            try { origin = java.net.URI.create(baseUrl); }
            catch (IllegalArgumentException error) { throw new ConfigurationException("Invalid gateway origin."); }
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null || (origin.getPath() != null && !origin.getPath().isEmpty())) {
                throw new ConfigurationException(
                        "'baseUrl' must be an HTTPS origin without credentials, path, query or fragment.");
            }
            if (environment == null) {
                throw new ConfigurationException("'environment' boş olamaz / 'environment' must not be null.");
            }
            authTokenPath = "/" + requireText(authTokenPath, "authTokenPath").replaceAll("^/+", "");
            if (!authTokenPath.matches("/[a-zA-Z0-9_/-]+") || authTokenPath.contains("//")) { throw new ConfigurationException("Invalid authentication path."); }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new ConfigurationException(
                        "'timeout' sıfırdan büyük olmalı / 'timeout' must be greater than zero.");
            }
            if (maxRetries < 0) {
                throw new ConfigurationException(
                        "'maxRetries' negatif olamaz / 'maxRetries' must not be negative.");
            }
            userAgent = requireText(userAgent, "userAgent");
            if (transport == null) {
                transport = new JdkHttpTransport();
            }
            if (tokenStore == null) {
                tokenStore = new com.lunixi.sdk.auth.InMemoryTokenStore();
            }
            return new LunixiConfig(this);
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new ConfigurationException(
                        "'" + name + "' zorunlu / '" + name + "' is required and must not be blank.");
            }
            return value.trim();
        }
    }
}
