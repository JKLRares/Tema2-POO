package main;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Shared JSON configuration. Only explicit ticket and user subtypes can be deserialized. */
public final class JsonSupport {
    private JsonSupport() {
    }

    public static ObjectMapper mapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        return new ObjectMapper(factory).registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
