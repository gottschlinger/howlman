package gottschlinger.howlman.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreGeneratorTest {

    private final PreGenerator generator = new PreGenerator();

    @Test
    void uuid_isValidFormat() {
        Map<String, String> result = generator.generate(Map.of("requestId", "UUID"));
        assertTrue(result.containsKey("requestId"));
        assertDoesNotThrow(() -> java.util.UUID.fromString(result.get("requestId")));
    }

    @Test
    void timestamp_isNumeric() {
        Map<String, String> result = generator.generate(Map.of("ts", "TIMESTAMP"));
        assertTrue(result.containsKey("ts"));
        assertDoesNotThrow(() -> Long.parseLong(result.get("ts")));
        assertTrue(Long.parseLong(result.get("ts")) > 0);
    }

    @Test
    void randomInt_isNumeric() {
        Map<String, String> result = generator.generate(Map.of("n", "RANDOM_INT"));
        assertTrue(result.containsKey("n"));
        assertDoesNotThrow(() -> Integer.parseInt(result.get("n")));
    }

    @Test
    void multipleVars() {
        Map<String, String> specs = Map.of("id", "UUID", "ts", "TIMESTAMP", "n", "RANDOM_INT");
        Map<String, String> result = generator.generate(specs);
        assertEquals(3, result.size());
    }

    @Test
    void unknownTypeIsSkipped() {
        Map<String, String> result = generator.generate(Map.of("x", "BOGUS"));
        assertTrue(result.isEmpty());
    }

    @Test
    void nullInputReturnsEmpty() {
        assertTrue(generator.generate(null).isEmpty());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(generator.generate(Map.of()).isEmpty());
    }

    @Test
    void eachUuidIsUnique() {
        Map<String, String> r1 = generator.generate(Map.of("id", "UUID"));
        Map<String, String> r2 = generator.generate(Map.of("id", "UUID"));
        assertNotEquals(r1.get("id"), r2.get("id"));
    }
}
