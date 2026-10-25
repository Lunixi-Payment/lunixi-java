package com.lunixi.sdk.json;

/** The text handed to {@link Json#parse(String)} is not valid JSON. */
public class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }
}
