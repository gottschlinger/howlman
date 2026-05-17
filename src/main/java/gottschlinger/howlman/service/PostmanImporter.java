package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class PostmanImporter {

    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isCollection(JsonNode root) {
        return root.has("info") && root.has("item");
    }

    public boolean isEnvironment(JsonNode root) {
        return root.has("values") && root.has("_postman_variable_scope");
    }

    /**
     * Auto-detects collection vs environment and returns an ImportResult.
     * A collection export also yields a companion environment if it carries collection-level variables.
     */
    public ImportResult importFile(Path file, String nameOverride) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        ImportResult result = new ImportResult();

        if (isCollection(root)) {
            RequestCollection collection = parseCollection(root, nameOverride);
            result.getCollections().add(collection);

            JsonNode vars = root.path("variable");
            if (vars.isArray() && vars.size() > 0) {
                result.getEnvironments().add(parseCollectionVars(vars, collection.getName() + "-vars"));
            }
        } else if (isEnvironment(root)) {
            result.getEnvironments().add(parseEnvironment(root, nameOverride));
        } else {
            throw new IOException("Unrecognized Postman file format (expected collection v2.1 or environment export)");
        }

        return result;
    }

    // â”€â”€ Collection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private RequestCollection parseCollection(JsonNode root, String nameOverride) {
        String name = nameOverride != null
                ? nameOverride
                : root.path("info").path("name").asText("imported");
        AuthConfig collectionAuth = parseAuth(root.path("auth"));
        RequestCollection col = new RequestCollection(name);
        populateItems(root.path("item"), col.getRequests(), col.getFolders(), collectionAuth);
        return col;
    }

    private void populateItems(JsonNode items, List<SavedRequest> requests,
                               List<RequestFolder> folders, AuthConfig inheritedAuth) {
        if (!items.isArray()) return;
        for (JsonNode item : items) {
            if (item.has("item")) {
                // Folder node
                RequestFolder folder = new RequestFolder(item.path("name").asText("folder"));
                AuthConfig folderAuth = parseAuth(item.path("auth"));
                AuthConfig effectiveAuth = folderAuth != null ? folderAuth : inheritedAuth;
                populateItems(item.path("item"), folder.getRequests(), folder.getFolders(), effectiveAuth);
                folders.add(folder);
            } else if (item.has("request")) {
                SavedRequest req = parseRequest(item, inheritedAuth);
                if (req != null) requests.add(req);
            }
        }
    }

    private SavedRequest parseRequest(JsonNode item, AuthConfig inheritedAuth) {
        JsonNode requestNode = item.path("request");
        SavedRequest req = new SavedRequest();
        req.setName(item.path("name").asText("unnamed"));
        req.setMethod(parseMethod(requestNode.path("method").asText("GET")));

        JsonNode urlNode = requestNode.path("url");
        req.setUrl(urlNode.isTextual() ? urlNode.asText() : urlNode.path("raw").asText(""));

        // Headers
        JsonNode headers = requestNode.path("header");
        if (headers.isArray() && headers.size() > 0) {
            Map<String, String> headerMap = new LinkedHashMap<>();
            for (JsonNode h : headers) {
                if (!h.path("disabled").asBoolean(false)) {
                    headerMap.put(h.path("key").asText(), h.path("value").asText());
                }
            }
            if (!headerMap.isEmpty()) req.setHeaders(headerMap);
        }

        // Auth â€” request-level wins; fall back to collection-level
        JsonNode authNode = requestNode.path("auth");
        if (!authNode.isMissingNode()) {
            String authType = authNode.path("type").asText("none");
            if (!"noauth".equals(authType) && !"none".equals(authType)) {
                req.setAuth(parseAuth(authNode));
            }
        } else if (inheritedAuth != null && inheritedAuth.getType() != AuthType.NONE) {
            req.setAuth(inheritedAuth);
        }

        // Body
        JsonNode bodyNode = requestNode.path("body");
        if (!bodyNode.isMissingNode()) {
            applyBody(req, bodyNode);
        }

        return req;
    }

    private void applyBody(SavedRequest req, JsonNode bodyNode) {
        String mode = bodyNode.path("mode").asText("none");
        switch (mode) {
            case "raw": {
                String raw = bodyNode.path("raw").asText("");
                if (!raw.isEmpty()) req.setBody(raw);
                String lang = bodyNode.path("options").path("raw").path("language").asText("");
                req.setBodyType("json".equals(lang) ? BodyType.JSON : BodyType.RAW);
                break;
            }
            case "urlencoded":
            case "formdata": {
                req.setBodyType(BodyType.FORM);
                JsonNode fields = bodyNode.path("urlencoded".equals(mode) ? "urlencoded" : "formdata");
                if (fields.isArray() && fields.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode f : fields) {
                        if (!f.path("disabled").asBoolean(false)) {
                            if (sb.length() > 0) sb.append("&");
                            sb.append(f.path("key").asText()).append("=").append(f.path("value").asText());
                        }
                    }
                    if (sb.length() > 0) req.setBody(sb.toString());
                }
                break;
            }
            default:
                break;
        }
    }

    private AuthConfig parseAuth(JsonNode authNode) {
        if (authNode == null || authNode.isMissingNode()) return null;
        String type = authNode.path("type").asText("none");
        AuthConfig auth = new AuthConfig();
        switch (type.toLowerCase()) {
            case "bearer": {
                auth.setType(AuthType.BEARER);
                for (JsonNode kv : authNode.path("bearer")) {
                    if ("token".equals(kv.path("key").asText())) {
                        auth.setToken(kv.path("value").asText());
                    }
                }
                return auth;
            }
            case "basic": {
                auth.setType(AuthType.BASIC);
                for (JsonNode kv : authNode.path("basic")) {
                    String key = kv.path("key").asText();
                    String val = kv.path("value").asText();
                    if ("username".equals(key)) auth.setUsername(val);
                    else if ("password".equals(key)) auth.setPassword(val);
                }
                return auth;
            }
            default:
                return null;
        }
    }

    // â”€â”€ Environment â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Environment parseEnvironment(JsonNode root, String nameOverride) {
        String name = nameOverride != null ? nameOverride : root.path("name").asText("imported");
        Environment env = new Environment(name);
        Map<String, String> vars = new LinkedHashMap<>();
        for (JsonNode v : root.path("values")) {
            if (v.path("enabled").asBoolean(true)) {
                String key = v.path("key").asText();
                if (!key.isEmpty()) vars.put(key, v.path("value").asText());
            }
        }
        env.setVariables(vars);
        return env;
    }

    private Environment parseCollectionVars(JsonNode varArray, String envName) {
        Environment env = new Environment(envName);
        Map<String, String> vars = new LinkedHashMap<>();
        for (JsonNode v : varArray) {
            String key = v.path("key").asText();
            if (!key.isEmpty()) vars.put(key, v.path("value").asText());
        }
        env.setVariables(vars);
        return env;
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private HttpMethod parseMethod(String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HttpMethod.GET;
        }
    }
}
