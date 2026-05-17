package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gottschlinger.howlman.model.AuthConfig;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.RequestFolder;
import gottschlinger.howlman.model.SavedRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InsomniaExporter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void export(RequestCollection col, List<Environment> environments, Path dest) throws IOException {
        AtomicInteger counter = new AtomicInteger(0);

        ObjectNode root = mapper.createObjectNode();
        root.put("__export_format", 4);
        root.put("__export_date", Instant.now().toString());
        root.put("__export_source", "howlman");

        ArrayNode resources = root.putArray("resources");

        String workspaceId = "wrk_" + sanitize(col.getName());
        resources.addObject()
                .put("_id", workspaceId)
                .put("_type", "workspace")
                .put("name", col.getName())
                .put("scope", "collection");

        addRequests(resources, col.getRequests(), workspaceId, counter);
        addFolders(resources, col.getFolders(), workspaceId, counter);

        if (environments != null) {
            for (Environment env : environments) {
                ObjectNode envNode = resources.addObject();
                envNode.put("_id", "env_" + sanitize(env.getName()) + "_" + counter.incrementAndGet());
                envNode.put("_type", "environment");
                envNode.put("parentId", workspaceId);
                envNode.put("name", env.getName());
                ObjectNode data = envNode.putObject("data");
                if (env.getVariables() != null) env.getVariables().forEach(data::put);
            }
        }

        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        mapper.writeValue(dest.toFile(), root);
    }

    public void export(RequestCollection col, Path dest) throws IOException {
        export(col, null, dest);
    }

    private void addRequests(ArrayNode resources, List<SavedRequest> requests,
                             String parentId, AtomicInteger counter) {
        if (requests == null) return;
        for (SavedRequest req : requests) resources.add(toRequest(req, parentId, counter));
    }

    private void addFolders(ArrayNode resources, List<RequestFolder> folders,
                            String parentId, AtomicInteger counter) {
        if (folders == null) return;
        for (RequestFolder folder : folders) {
            String groupId = "grp_" + sanitize(folder.getName()) + "_" + counter.incrementAndGet();
            resources.addObject()
                    .put("_id", groupId)
                    .put("_type", "request_group")
                    .put("parentId", parentId)
                    .put("name", folder.getName());
            addRequests(resources, folder.getRequests(), groupId, counter);
            addFolders(resources, folder.getFolders(), groupId, counter);
        }
    }

    private ObjectNode toRequest(SavedRequest req, String parentId, AtomicInteger counter) {
        ObjectNode node = mapper.createObjectNode();
        node.put("_id", "req_" + sanitize(req.getName()) + "_" + counter.incrementAndGet());
        node.put("_type", "request");
        node.put("parentId", parentId);
        node.put("name", req.getName() != null ? req.getName() : "");
        node.put("method", req.getMethod() != null ? req.getMethod().name() : "GET");
        node.put("url", req.getUrl() != null ? req.getUrl() : "");

        ArrayNode headers = node.putArray("headers");
        if (req.getHeaders() != null) {
            req.getHeaders().forEach((k, v) ->
                    headers.addObject().put("name", k).put("value", v));
        }

        AuthConfig auth = req.getAuth();
        ObjectNode authNode = node.putObject("authentication");
        if (auth != null && auth.getType() != null && auth.getType() != AuthType.NONE) {
            if (auth.getType() == AuthType.BEARER) {
                authNode.put("type", "bearer");
                authNode.put("token", auth.getToken() != null ? auth.getToken() : "");
            } else if (auth.getType() == AuthType.BASIC) {
                authNode.put("type", "basic");
                authNode.put("username", auth.getUsername() != null ? auth.getUsername() : "");
                authNode.put("password", auth.getPassword() != null ? auth.getPassword() : "");
            }
        }

        ObjectNode body = node.putObject("body");
        if (req.getBodyType() != null && req.getBodyType() != BodyType.NONE
                && req.getBody() != null && !req.getBody().isBlank()) {
            if (req.getBodyType() == BodyType.JSON) {
                body.put("mimeType", "application/json");
                body.put("text", req.getBody());
            } else if (req.getBodyType() == BodyType.FORM) {
                body.put("mimeType", "application/x-www-form-urlencoded");
                body.put("text", req.getBody());
            }
        }

        return node;
    }

    private static String sanitize(String name) {
        if (name == null) return "unnamed";
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }
}
