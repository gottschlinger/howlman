package gottschlinger.howlman.service;

import gottschlinger.howlman.model.GeneratorType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PreGenerator {

    private static final Random RANDOM = new Random();

    public Map<String, String> generate(Map<String, String> preVars) {
        Map<String, String> result = new LinkedHashMap<>();
        if (preVars == null || preVars.isEmpty()) return result;

        for (Map.Entry<String, String> entry : preVars.entrySet()) {
            String varName = entry.getKey();
            String typeName = entry.getValue();

            GeneratorType type;
            try {
                type = GeneratorType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: unknown generator type '" + typeName + "'; skipping " + varName);
                continue;
            }

            result.put(varName, switch (type) {
                case UUID        -> UUID.randomUUID().toString();
                case TIMESTAMP   -> String.valueOf(Instant.now().toEpochMilli());
                case RANDOM_INT  -> String.valueOf(RANDOM.nextInt(Integer.MAX_VALUE));
            });
        }

        return result;
    }
}
