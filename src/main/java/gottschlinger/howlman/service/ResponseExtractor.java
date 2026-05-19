package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResponseExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, String> extract(HttpResponse<String> response, List<String> specs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (specs == null || specs.isEmpty()) return result;

        String body = response.body();
        if (body == null || body.isBlank()) {
            System.err.println("Warning: response body is empty; nothing to extract.");
            return result;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            System.err.println("Warning: response body is not valid JSON; cannot extract variables.");
            return result;
        }

        for (String spec : specs) {
            int eq = spec.indexOf('=');
            if (eq < 1 || eq == spec.length() - 1) {
                System.err.println("Warning: skipping malformed --extract spec (expected varName=json.path): " + spec);
                continue;
            }
            String varName = spec.substring(0, eq).trim();
            String path = spec.substring(eq + 1).trim();

            JsonNode node = traverse(root, path);
            if (node == null || node.isMissingNode() || node.isNull()) {
                System.err.println("Warning: path '" + path + "' not found in response; skipping.");
                continue;
            }
            result.put(varName, node.isValueNode() ? node.asText() : node.toString());
        }

        return result;
    }

    private JsonNode traverse(JsonNode root, String dotPath) {
        JsonNode current = root;
        for (String segment : dotPath.split("\\.")) {
            if (current == null || !current.isObject()) return null;
            current = current.get(segment);
        }
        return current;
    }
}
