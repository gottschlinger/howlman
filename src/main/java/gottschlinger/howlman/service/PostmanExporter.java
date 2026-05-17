package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gottschlinger.howlman.model.AuthConfig;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.RequestFolder;
import gottschlinger.howlman.model.SavedRequest;

import gottschlinger.howlman.model.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class PostmanExporter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void export(RequestCollection col, Path dest) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode info = root.putObject("info");
        info.put("name", col.getName());
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");

        ArrayNode items = root.putArray("item");
        addRequests(items, col.getRequests());
        addFolders(items, col.getFolders());

        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        mapper.writeValue(dest.toFile(), root);
    }

    public void exportEnvironments(List<Environment> environments, Path destDir,
                                   String collectionName) throws IOException {
        if (environments == null) return;
        Files.createDirectories(destDir);
        for (Environment env : environments) {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", UUID.randomUUID().toString());
            root.put("name", env.getName());
            ArrayNode values = root.putArray("values");
            if (env.getVariables() != null) {
                env.getVariables().forEach((k, v) ->
                        values.addObject().put("key", k).put("value", v).put("enabled", true));
            }
            root.put("_postman_variable_scope", "environment");
            root.put("_postman_exported_using", "HowlMan");
            String filename = "postman_" + collectionName + "_env_" + env.getName() + ".json";
            mapper.writeValue(destDir.resolve(filename).toFile(), root);
        }
    }

    private void addRequests(ArrayNode items, List<SavedRequest> requests) {
        if (requests == null) return;
        for (SavedRequest req : requests) items.add(toItem(req));
    }

    private void addFolders(ArrayNode items, List<RequestFolder> folders) {
        if (folders == null) return;
        for (RequestFolder folder : folders) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", folder.getName());
            ArrayNode sub = node.putArray("item");
            addRequests(sub, folder.getRequests());
            addFolders(sub, folder.getFolders());
            items.add(node);
        }
    }

    private ObjectNode toItem(SavedRequest req) {
        ObjectNode item = mapper.createObjectNode();
        item.put("name", req.getName() != null ? req.getName() : "");

        ObjectNode request = item.putObject("request");
        request.put("method", req.getMethod() != null ? req.getMethod().name() : "GET");

        request.put("url", req.getUrl() != null ? req.getUrl() : "");

        ArrayNode headers = request.putArray("header");
        if (req.getHeaders() != null) {
            req.getHeaders().forEach((k, v) ->
                    headers.addObject().put("key", k).put("value", v));
        }

        AuthConfig auth = req.getAuth();
        if (auth != null && auth.getType() != null && auth.getType() != AuthType.NONE) {
            ObjectNode authNode = request.putObject("auth");
            if (auth.getType() == AuthType.BEARER) {
                authNode.put("type", "bearer");
                authNode.putArray("bearer").addObject()
                        .put("key", "token")
                        .put("value", auth.getToken() != null ? auth.getToken() : "")
                        .put("type", "string");
            } else if (auth.getType() == AuthType.BASIC) {
                authNode.put("type", "basic");
                ArrayNode basic = authNode.putArray("basic");
                basic.addObject().put("key", "username")
                        .put("value", auth.getUsername() != null ? auth.getUsername() : "")
                        .put("type", "string");
                basic.addObject().put("key", "password")
                        .put("value", auth.getPassword() != null ? auth.getPassword() : "")
                        .put("type", "string");
            }
        }

        if (req.getBodyType() != null && req.getBodyType() != BodyType.NONE
                && req.getBody() != null && !req.getBody().isBlank()) {
            ObjectNode body = request.putObject("body");
            if (req.getBodyType() == BodyType.JSON) {
                body.put("mode", "raw");
                body.put("raw", req.getBody());
                body.putObject("options").putObject("raw").put("language", "json");
            } else if (req.getBodyType() == BodyType.FORM) {
                body.put("mode", "urlencoded");
                body.put("raw", req.getBody());
            }
        }

        return item;
    }
}
