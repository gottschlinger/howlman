package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class InsomniaImporter {

    private static final Pattern INSOMNIA_VAR = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isInsomnia(JsonNode root) {
        return root.has("__export_format") && root.path("__export_format").asInt() == 4;
    }

    public ImportResult importFile(Path file, String nameOverride) throws IOException {
        return parse(mapper.readTree(file.toFile()), nameOverride);
    }

    ImportResult parse(JsonNode root, String nameOverride) {
        ImportResult result = new ImportResult();
        JsonNode resources = root.path("resources");
        if (!resources.isArray()) return result;

        Map<String, JsonNode> byId = new LinkedHashMap<>();
        Map<String, String> workspaceNames = new LinkedHashMap<>();
        List<JsonNode> environments = new ArrayList<>();
        Map<String, List<JsonNode>> childrenByParent = new LinkedHashMap<>();

        for (JsonNode r : resources) {
            String type = r.path("_type").asText();
            String id = r.path("_id").asText();
            byId.put(id, r);

            switch (type) {
                case "workspace" -> workspaceNames.put(id, r.path("name").asText("imported"));
                case "environment" -> environments.add(r);
                case "request", "request_group" -> {
                    String parentId = r.path("parentId").asText(null);
                    if (parentId != null) {
                        childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(r);
                    }
                }
            }
        }

        for (JsonNode env : environments) {
            result.getEnvironments().add(parseEnvironment(env, nameOverride, workspaceNames, byId));
        }

        for (Map.Entry<String, String> ws : workspaceNames.entrySet()) {
            String wsId = ws.getKey();
            String colName = (nameOverride != null && workspaceNames.size() == 1)
                    ? nameOverride : ws.getValue();
            RequestCollection col = new RequestCollection(colName);
            populateCollection(wsId, col.getRequests(), col.getFolders(), childrenByParent);
            result.getCollections().add(col);
        }

        return result;
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void populateCollection(String parentId, List<SavedRequest> requests,
                                    List<RequestFolder> folders,
                                    Map<String, List<JsonNode>> childrenByParent) {
        for (JsonNode child : childrenByParent.getOrDefault(parentId, List.of())) {
            String type = child.path("_type").asText();
            if ("request".equals(type)) {
                requests.add(parseRequest(child));
            } else if ("request_group".equals(type)) {
                RequestFolder folder = new RequestFolder(child.path("name").asText("folder"));
                populateCollection(child.path("_id").asText(),
                        folder.getRequests(), folder.getFolders(), childrenByParent);
                folders.add(folder);
            }
        }
    }

    private SavedRequest parseRequest(JsonNode r) {
        SavedRequest req = new SavedRequest();
        req.setName(r.path("name").asText("unnamed"));
        req.setMethod(parseMethod(r.path("method").asText("GET")));
        req.setUrl(norm(r.path("url").asText("")));

        JsonNode headers = r.path("headers");
        if (headers.isArray() && headers.size() > 0) {
            Map<String, String> headerMap = new LinkedHashMap<>();
            for (JsonNode h : headers) {
                headerMap.put(h.path("name").asText(), norm(h.path("value").asText()));
            }
            if (!headerMap.isEmpty()) req.setHeaders(headerMap);
        }

        JsonNode auth = r.path("authentication");
        if (auth.isObject() && !auth.isEmpty()) {
            AuthConfig cfg = parseAuth(auth);
            if (cfg != null) req.setAuth(cfg);
        }

        JsonNode body = r.path("body");
        if (body.isObject() && !body.isEmpty()) {
            String mimeType = body.path("mimeType").asText("");
            String text = norm(body.path("text").asText(""));
            if (!text.isEmpty()) req.setBody(text);
            if (mimeType.contains("json")) {
                req.setBodyType(BodyType.JSON);
            } else if (mimeType.contains("form")) {
                req.setBodyType(BodyType.FORM);
            } else if (!text.isEmpty()) {
                req.setBodyType(BodyType.RAW);
            }
        }

        return req;
    }

    private AuthConfig parseAuth(JsonNode auth) {
        String type = auth.path("type").asText("none");
        AuthConfig cfg = new AuthConfig();
        switch (type.toLowerCase()) {
            case "bearer":
                cfg.setType(AuthType.BEARER);
                cfg.setToken(norm(auth.path("token").asText("")));
                return cfg;
            case "basic":
                cfg.setType(AuthType.BASIC);
                cfg.setUsername(norm(auth.path("username").asText("")));
                cfg.setPassword(norm(auth.path("password").asText("")));
                return cfg;
            default:
                return null;
        }
    }

    private Environment parseEnvironment(JsonNode r, String nameOverride,
                                         Map<String, String> workspaceNames,
                                         Map<String, JsonNode> byId) {
        String rawName = r.path("name").asText("imported");
        String wsId = workspaceAncestor(r.path("_id").asText(), byId);
        String name;
        if (nameOverride != null) {
            name = nameOverride;
        } else if ("Base Environment".equals(rawName) && wsId != null) {
            name = workspaceNames.getOrDefault(wsId, rawName);
        } else {
            name = rawName;
        }

        Environment env = new Environment(name);
        Map<String, String> vars = new LinkedHashMap<>();
        r.path("data").fields().forEachRemaining(e -> vars.put(e.getKey(), e.getValue().asText()));
        env.setVariables(vars);
        return env;
    }

    private String workspaceAncestor(String id, Map<String, JsonNode> byId) {
        Set<String> visited = new HashSet<>();
        String current = id;
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            JsonNode node = byId.get(current);
            if (node == null) return null;
            if ("workspace".equals(node.path("_type").asText())) return current;
            current = node.path("parentId").asText(null);
        }
        return null;
    }

    private String norm(String s) {
        if (s == null || s.isEmpty()) return s;
        return INSOMNIA_VAR.matcher(s).replaceAll("{{$1}}");
    }

    private HttpMethod parseMethod(String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HttpMethod.GET;
        }
    }
}
