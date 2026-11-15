package com.lunixi.sdk.wallet;

import java.util.List;

/** Wallet wire values ({@code /api/v1/wallet}). The same sets as the Node and PHP SDKs. */
public final class WalletValues {

    private WalletValues() {
    }

    /** {@code accountType} of an end user. */
    public static final class AccountType {
        public static final String PERSONAL = "PERSONAL";
        public static final String BUSINESS = "BUSINESS";
        public static final List<String> ALL = List.of(PERSONAL, BUSINESS);

        private AccountType() {
        }
    }

    /** {@code status} of an end user. */
    public static final class EndUserStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String FROZEN = "FROZEN";
        public static final String CLOSED = "CLOSED";
        public static final List<String> ALL = List.of(ACTIVE, FROZEN, CLOSED);

        private EndUserStatus() {
        }
    }

    /** {@code kycLevel} of an end user. */
    public static final class KycLevel {
        public static final String NONE = "NONE";
        public static final String LOW = "LOW";
        public static final String SUBSTANTIAL = "SUBSTANTIAL";
        public static final String HIGH = "HIGH";
        public static final List<String> ALL = List.of(NONE, LOW, SUBSTANTIAL, HIGH);

        private KycLevel() {
        }
    }

    /** {@code otpChannel} of a two-step money movement. */
    public static final class OtpChannel {
        public static final String EMAIL = "EMAIL";
        public static final String SMS = "SMS";
        public static final String PUSH = "PUSH";
        public static final List<String> ALL = List.of(EMAIL, SMS, PUSH);

        private OtpChannel() {
        }
    }

    /** {@code qrType}. Static codes carry no amount; dynamic ones require it. */
    public static final class QrType {
        public static final String P2P_STATIC = "P2P_STATIC";
        public static final String P2P_DYNAMIC = "P2P_DYNAMIC";
        public static final String MERCHANT_STATIC = "MERCHANT_STATIC";
        public static final String MERCHANT_DYNAMIC = "MERCHANT_DYNAMIC";
        public static final List<String> ALL = List.of(P2P_STATIC, P2P_DYNAMIC, MERCHANT_STATIC, MERCHANT_DYNAMIC);

        private QrType() {
        }
    }

    /** {@code format} of {@link WalletQr#image}. */
    public static final class QrImageFormat {
        public static final String SVG = "svg";
        public static final String PNG = "png";
        public static final List<String> ALL = List.of(SVG, PNG);

        private QrImageFormat() {
        }
    }

    /** {@code targetType} of a bulk payout item. */
    public static final class BulkTargetType {
        public static final String WALLET = "WALLET";
        public static final String IBAN = "IBAN";
        public static final List<String> ALL = List.of(WALLET, IBAN);

        private BulkTargetType() {
        }
    }

    /** {@code status} of a corporate account. */
    public static final class CorporateAccountStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String ARCHIVED = "ARCHIVED";
        public static final List<String> ALL = List.of(ACTIVE, ARCHIVED);

        private CorporateAccountStatus() {
        }
    }
}
