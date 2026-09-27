package com.chris64233.loancovenant.web;

import tools.jackson.databind.ObjectMapper;

/** 测试 JSON 小工具。 */
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    static long readLong(String json, String field) {
        try {
            return MAPPER.readTree(json).get(field).asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
