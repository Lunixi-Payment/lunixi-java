package com.lunixi.sdk.bankconnect;

import java.util.List;
import java.util.Map;

/**
 * Bank account wire values and query filters ({@code /api/v1/bank-connect}, product {@code BANK_CONNECT}). The same
 * sets as the Node SDK's {@code bank-connect-values.js} and the PHP SDK's {@code BankConnect\*} constants.
 */
public final class BankConnectValues {

    /** Query filters each read accepts (the gateway rejects any other with 400). */
    public static final Map<String, List<String>> FILTERS = Map.of(
            "listBanks", List.of("limit", "cursor"),
            "listConnections", List.of("limit", "cursor", "status", "bankId"),
            "listAccounts", List.of("limit", "cursor", "connectionId", "bankId", "currency", "status"),
            "listBalances", List.of("limit", "cursor", "accountId", "currency"),
            "listTransactions", List.of("limit", "cursor", "accountId", "bookedFrom", "bookedTo", "direction",
                    "minAmountMinor", "maxAmountMinor", "canonicalType"));
    /** Largest page ({@code limit} 1–100; the gateway's default is 25). */
    public static final int MAX_LIMIT = 100;
    /** Most account ids one {@code accountId} filter may carry. */
    public static final int MAX_ACCOUNT_FILTER = 100;
    /** Widest {@code bookedFrom} … {@code bookedTo} window of one transaction request, in days. */
    public static final int BOOKED_RANGE_MAX_DAYS = 92;
    /** Longest opaque cursor. */
    public static final int CURSOR_MAX_LENGTH = 2048;

    private BankConnectValues() {
    }

    /**
     * Status of a bank connection. Data is read only while a connection is {@code ACTIVE} or {@code DEGRADED}
     * ({@code DEGRADED}: the last reads from the bank failed, so the data may be older; see the account's
     * {@code freshness}). {@code requestSync} on any other status is 409 {@code bank_connect.connection.not_active}.
     */
    public static final class ConnectionStatus {
        public static final String PENDING_DOCUMENTS = "PENDING_DOCUMENTS";
        public static final String UNDER_REVIEW = "UNDER_REVIEW";
        public static final String PENDING_SETUP = "PENDING_SETUP";
        public static final String ACTIVE = "ACTIVE";
        public static final String DEGRADED = "DEGRADED";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String DISCONNECTED = "DISCONNECTED";
        public static final List<String> ALL = List.of(PENDING_DOCUMENTS, UNDER_REVIEW, PENDING_SETUP, ACTIVE,
                DEGRADED, SUSPENDED, DISCONNECTED);

        private ConnectionStatus() {
        }
    }

    /** Status of a connected account. */
    public static final class AccountStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final List<String> ALL = List.of(ACTIVE, INACTIVE);

        private AccountStatus() {
        }
    }

    /** Money in ({@code CREDIT}) or out ({@code DEBIT}). {@code amountMinor} is always positive. */
    public static final class TransactionDirection {
        public static final String CREDIT = "CREDIT";
        public static final String DEBIT = "DEBIT";
        public static final List<String> ALL = List.of(CREDIT, DEBIT);

        private TransactionDirection() {
        }
    }

    /** Lunixi's bank-independent transaction type ({@code canonicalType}). */
    public static final class TransactionType {
        public static final String TRANSFER_IN = "TRANSFER_IN";
        public static final String TRANSFER_OUT = "TRANSFER_OUT";
        public static final String INTERNAL_IN = "INTERNAL_IN";
        public static final String INTERNAL_OUT = "INTERNAL_OUT";
        public static final String POS_SETTLEMENT = "POS_SETTLEMENT";
        public static final String POS_SETTLEMENT_REFUND = "POS_SETTLEMENT_REFUND";
        public static final String FEE = "FEE";
        public static final String TAX = "TAX";
        public static final String CASH_IN = "CASH_IN";
        public static final String CASH_OUT = "CASH_OUT";
        public static final String CARD = "CARD";
        public static final String LOAN = "LOAN";
        public static final String FX = "FX";
        public static final String INTEREST = "INTEREST";
        public static final String OTHER = "OTHER";
        public static final List<String> ALL = List.of(TRANSFER_IN, TRANSFER_OUT, INTERNAL_IN, INTERNAL_OUT,
                POS_SETTLEMENT, POS_SETTLEMENT_REFUND, FEE, TAX, CASH_IN, CASH_OUT, CARD, LOAN, FX, INTEREST, OTHER);

        private TransactionType() {
        }
    }

    /** {@code AVAILABLE}: the bank can be connected (connections are started in the Lunixi dashboard). */
    public static final class Availability {
        public static final String AVAILABLE = "AVAILABLE";
        public static final String COMING_SOON = "COMING_SOON";
        public static final List<String> ALL = List.of(AVAILABLE, COMING_SOON);

        private Availability() {
        }
    }

    /**
     * What a bank or a connection offers ({@code capabilities}). {@code OUTGOING_TRANSFER} and {@code FX_EXCHANGE}:
     * the connection can send transfers ({@link BankConnectClient#transfers()}) and exchange currency
     * ({@link BankConnectClient#fx()}).
     */
    public static final class Capability {
        public static final String ACCOUNT_LIST = "ACCOUNT_LIST";
        public static final String BALANCE = "BALANCE";
        public static final String TRANSACTIONS = "TRANSACTIONS";
        public static final String OUTGOING_TRANSFER = "OUTGOING_TRANSFER";
        public static final String FX_EXCHANGE = "FX_EXCHANGE";
        public static final List<String> ALL = List.of(ACCOUNT_LIST, BALANCE, TRANSACTIONS, OUTGOING_TRANSFER,
                FX_EXCHANGE);

        private Capability() {
        }
    }

    /** {@code QUEUED}: the read starts now or is already waiting. {@code SCHEDULED}: it runs at {@code scheduledAt}. */
    public static final class SyncStatus {
        public static final String QUEUED = "QUEUED";
        public static final String SCHEDULED = "SCHEDULED";
        public static final List<String> ALL = List.of(QUEUED, SCHEDULED);

        private SyncStatus() {
        }
    }

    /**
     * Status of an outgoing transfer. {@code PENDING_OTP}: started in the dashboard, waiting for the user's e-mail
     * code (an API-key transfer never has it). {@code SUBMITTED}: approved and queued or sent to the bank.
     * {@code PROCESSING}: the bank is processing it. {@code COMPLETED}: the bank completed it; it can still become
     * {@code RETURNED}. {@code FAILED}, {@code RETURNED}, {@code CANCELED} are final.
     */
    public static final class TransferStatus {
        public static final String PENDING_OTP = "PENDING_OTP";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String PROCESSING = "PROCESSING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String RETURNED = "RETURNED";
        public static final String CANCELED = "CANCELED";
        public static final List<String> ALL = List.of(PENDING_OTP, SUBMITTED, PROCESSING, COMPLETED, FAILED,
                RETURNED, CANCELED);

        private TransferStatus() {
        }
    }

    /** The rail the bank chose: own accounts, same bank, FAST, EFT; {@code UNKNOWN} until it says. */
    public static final class TransferRail {
        public static final String INTERNAL = "INTERNAL";
        public static final String INTRABANK = "INTRABANK";
        public static final String FAST = "FAST";
        public static final String EFT = "EFT";
        public static final String UNKNOWN = "UNKNOWN";
        public static final List<String> ALL = List.of(INTERNAL, INTRABANK, FAST, EFT, UNKNOWN);

        private TransferRail() {
        }
    }

    /** Where the transfer or exchange order was started. */
    public static final class TransferInitiatedVia {
        public static final String PANEL = "PANEL";
        public static final String API = "API";
        public static final List<String> ALL = List.of(PANEL, API);

        private TransferInitiatedVia() {
        }
    }

    /** Status of a currency exchange order. An order is created {@code SUBMITTED}. */
    public static final class FxOrderStatus {
        public static final String SUBMITTED = "SUBMITTED";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final List<String> ALL = List.of(SUBMITTED, COMPLETED, FAILED);

        private FxOrderStatus() {
        }
    }

    /** Which amount of a quote you fixed: {@code SOURCE} (sell amount) or {@code TARGET} (buy amount). */
    public static final class FxRequestedSide {
        public static final String SOURCE = "SOURCE";
        public static final String TARGET = "TARGET";
        public static final List<String> ALL = List.of(SOURCE, TARGET);

        private FxRequestedSide() {
        }
    }
}
