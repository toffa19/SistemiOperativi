package it.unibo.labso.p2p.common;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonCodec {
    private static final ObjectMapper M = new ObjectMapper();
    private JsonCodec() {}
    public static String toJson(Object o) {
        try { return M.writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); }
    }
    public static <T> T fromJson(String s, Class<T> c) {
        try { return M.readValue(s, c); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
