package com.lunixi.sdk.reference;

import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Lunixi address dictionary for Türkiye (province → district → neighborhood). The codes fill an {@code ADDRESS}
 * input field, e.g. the debtor address of a Senetle Ödeme payment:
 * {@code {provinceCode, districtCode, neighborhoodCode, line}}.
 *
 * <pre>
 * GET /api/v1/reference/tr-address/provinces
 * GET /api/v1/reference/tr-address/districts?provinceCode=06
 * GET /api/v1/reference/tr-address/neighborhoods?districtCode=…
 * </pre>
 *
 * <p>Public reference data (no personal data); the answer is the gateway envelope with
 * {@code data.items: [{code, name}]}. An empty list means the data for that level is not published yet: show that
 * state instead of inventing codes.
 */
public final class ReferenceClient {

    private static final String BASE = "/api/v1/reference/tr-address";
    private static final Pattern PROVINCE_CODE = Pattern.compile("0[1-9]|[1-7][0-9]|8[01]");
    private static final Pattern DISTRICT_CODE = Pattern.compile("[0-9]{1,12}");

    private final ApiClient api;

    public ReferenceClient(ApiClient api) {
        this.api = api;
    }

    /** @return provinces with their Lunixi code ({@code 01}–{@code 81}) */
    public Map<String, Object> listProvinces() {
        return api.request("GET", BASE + "/provinces", null, RequestOptions.none());
    }

    /**
     * @param provinceCode a two-digit province code from {@code 01} to {@code 81}
     * @return the districts of the province
     */
    public Map<String, Object> listDistricts(String provinceCode) {
        if (provinceCode == null || !PROVINCE_CODE.matcher(provinceCode).matches()) {
            throw new ConfigurationException("provinceCode must be a two-digit province code from 01 to 81; got "
                    + provinceCode + ".");
        }
        return api.request("GET", BASE + "/districts", null,
                RequestOptions.builder().query("provinceCode", provinceCode).build());
    }

    /**
     * @param districtCode a district code (digits) from {@link #listDistricts(String)}
     * @return the neighborhoods of the district
     */
    public Map<String, Object> listNeighborhoods(String districtCode) {
        if (districtCode == null || !DISTRICT_CODE.matcher(districtCode).matches()) {
            throw new ConfigurationException("districtCode must be a district code (digits) from listDistricts(); got "
                    + districtCode + ".");
        }
        return api.request("GET", BASE + "/neighborhoods", null,
                RequestOptions.builder().query("districtCode", districtCode).build());
    }
}
