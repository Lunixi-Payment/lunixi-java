package com.lunixi.sdk.payment;

import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.exception.LunixiApiException;
import java.nio.file.Path;
import java.util.Map;

/** Fresh JVM probe: keep only actual disk state between independently created SDK clients. */
public final class DurableCommerceProbe {
    private DurableCommerceProbe() { }
    public static void main(String[] args) {
        DurableCommerceFixture.FileLedger ledger = new DurableCommerceFixture.FileLedger(Path.of(args[0]));
        DurableCommerceFixture.Gateway gateway = new DurableCommerceFixture.Gateway(ledger, true);
        CommerceService service = new CommerceService(DurableCommerceFixture.client(gateway), ledger, DurableCommerceFixture.box());
        try {
            service.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT,
                    "https://shop.invalid/return", true, false, "en");
            throw new AssertionError("Lost response must remain unresolved.");
        } catch (LunixiApiException expected) {
            if (gateway.bodies.size() != 1) { throw new AssertionError("Expected exactly one financial dispatch."); }
            System.out.println(Json.stringify(Map.of("body", gateway.bodies.get(0), "key", gateway.keys.get(0))));
        }
    }
}
