package com.lunixi.sdk.bankconnect;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RequestOptions;

/** The filter checks of the bank account reads, transfers and exchange orders; nothing is sent when one fails. */
final class BankConnectRules {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private BankConnectRules() {
    }

    /**
     * Checks the filters of one list route and adds them to a signed request.
     *
     * @param filters the filters, or {@code null}
     * @param allowed the accepted filter names
     * @param route   the route name (selects the value sets)
     * @return signed options with the query
     */
    static RequestOptions query(Map<String, ?> filters, List<String> allowed, String route) {
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        for (Map.Entry<String, String> entry : values(filters, allowed, route).entrySet()) {
            options.query(entry.getKey(), entry.getValue());
        }
        return options.build();
    }

    static Map<String, String> values(Map<String, ?> filters, List<String> allowed, String route) {
        Map<String, ?> given = filters == null ? Map.of() : filters;
        List<String> unknown = new ArrayList<>();
        for (String key : given.keySet()) {
            if (!allowed.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown filter(s): " + String.join(", ", unknown) + ". Allowed: "
                    + String.join(", ", allowed) + ". The gateway rejects unknown query fields with 400.");
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String key : allowed) {
            Object value = given.get(key);
            if (value == null || "".equals(value) || (value instanceof Collection<?> list && list.isEmpty())) {
                continue;
            }
            query.put(key, value(key, value, route));
        }
        return query;
    }

    private static String value(String key, Object value, String route) {
        switch (key) {
            case "limit":
                return Long.toString(Checks.limit(value, BankConnectValues.MAX_LIMIT));
            case "cursor":
                return Checks.cursor(value, BankConnectValues.CURSOR_MAX_LENGTH);
            case "status":
                return switch (route) {
                    case "listAccounts" -> Checks.oneOf(value, BankConnectValues.AccountStatus.ALL, key);
                    case "listTransfers" -> Checks.listOf(value, BankConnectValues.TransferStatus.ALL, key);
                    case "listFxOrders" -> Checks.listOf(value, BankConnectValues.FxOrderStatus.ALL, key);
                    default -> Checks.listOf(value, BankConnectValues.ConnectionStatus.ALL, key);
                };
            case "direction":
                return Checks.oneOf(value, BankConnectValues.TransactionDirection.ALL, key);
            case "canonicalType":
                return Checks.listOf(value, BankConnectValues.TransactionType.ALL, key);
            case "accountId":
                return "listTransfers".equals(route) ? Checks.text(value, key) : idList(value);
            case "bankId":
            case "connectionId":
                return Checks.text(value, key);
            case "currency":
                return Checks.currency(value, key);
            case "bookedFrom":
            case "bookedTo":
            case "createdFrom":
            case "createdTo":
                return Checks.isoTime(value, key);
            case "minAmountMinor":
            case "maxAmountMinor":
                return Checks.minor(value, key, false);
            default:
                throw new ConfigurationException("Unknown filter '" + key + "'.");
        }
    }

    private static String idList(Object value) {
        Collection<?> ids = value instanceof Collection<?> list ? list : List.of(value);
        if (ids.size() > BankConnectValues.MAX_ACCOUNT_FILTER) {
            throw new ConfigurationException("'accountId' takes at most " + BankConnectValues.MAX_ACCOUNT_FILTER
                    + " account ids.");
        }
        List<String> out = new ArrayList<>();
        for (Object id : ids) {
            if (!(id instanceof String text) || text.isBlank() || text.contains(",")) {
                throw new ConfigurationException("'accountId' must be account ids (strings without commas).");
            }
            out.add(text.trim());
        }
        return String.join(",", out);
    }

    /**
     * The transaction window and amount rules, checked locally: {@code bookedTo} needs {@code bookedFrom},
     * {@code bookedFrom <= bookedTo}, at most 92 days apart, {@code minAmountMinor <= maxAmountMinor}.
     */
    static void assertTransactionRules(Map<String, ?> filters, Map<String, String> query) {
        Map<String, ?> given = filters == null ? Map.of() : filters;
        if (Checks.present(given.get("bookedTo")) && !Checks.present(given.get("bookedFrom"))) {
            throw new ConfigurationException("'bookedTo' needs 'bookedFrom': a window without a start is unbounded.");
        }
        Long from = Checks.epochMillis(given.get("bookedFrom"));
        Long to = Checks.epochMillis(given.get("bookedTo"));
        if (from != null && to != null) {
            if (from > to) {
                throw new ConfigurationException("'bookedFrom' must not be after 'bookedTo'.");
            }
            if (to - from > BankConnectValues.BOOKED_RANGE_MAX_DAYS * DAY_MS) {
                throw new ConfigurationException("'bookedFrom' and 'bookedTo' may be at most "
                        + BankConnectValues.BOOKED_RANGE_MAX_DAYS + " days apart. Page through narrower windows.");
            }
        }
        String min = query.get("minAmountMinor");
        String max = query.get("maxAmountMinor");
        if (min != null && max != null && new BigInteger(min).compareTo(new BigInteger(max)) > 0) {
            throw new ConfigurationException("'minAmountMinor' must not be greater than 'maxAmountMinor'.");
        }
    }
}
