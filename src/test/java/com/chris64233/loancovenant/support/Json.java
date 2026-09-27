package com.chris64233.loancovenant.support;

import tools.jackson.databind.ObjectMapper;

public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static long readLong(String json, String field) {
        try {
            return MAPPER.readTree(json).get(field).asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
