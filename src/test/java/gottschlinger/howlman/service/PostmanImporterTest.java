package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PostmanImporterTest {

    @TempDir
    Path tempDir;

    ObjectMapper mapper;
    PostmanImporter importer;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        importer = new PostmanImporter();
    }

    // â”€â”€ Detection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void isCollection_returnsTrueForCollectionJson() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("info").put("name", "test");
        root.putArray("item");
        assertTrue(importer.isCollection(root));
        assertFalse(importer.isEnvironment(root));
    }

    @Test
    void isEnvironment_returnsTrueForEnvironmentJson() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("values");
        root.put("_postman_variable_scope", "environment");
        assertTrue(importer.isEnvironment(root));
        assertFalse(importer.isCollection(root));
    }

    // â”€â”€ Collection import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void importCollection_mapsNameAndRequests() throws Exception {
        Path file = buildCollectionFile("my-api", null);
        ImportResult result = importer.importFile(file, null);

        assertEquals(1, result.getCollections().size());
        assertEquals("my-api", result.getCollections().get(0).getName());
        assertEquals(1, result.getCollections().get(0).getRequests().size());
    }

    @Test
    void importCollection_nameOverrideApplied() throws Exception {
        Path file = buildCollectionFile("original-name", null);
        ImportResult result = importer.importFile(file, "override-name");

        assertEquals("override-name", result.getCollections().get(0).getName());
    }

    @Test
    void importCollection_mapsRequestFields() throws Exception {
        Path file = buildCollectionFile("api", null);
        ImportResult result = importer.importFile(file, null);

        SavedRequest req = result.getCollections().get(0).getRequests().get(0);
        assertEquals("get-users", req.getName());
        assertEquals(HttpMethod.GET, req.getMethod());
        assertEquals("{{baseUrl}}/users", req.getUrl());
        assertEquals("application/json", req.getHeaders().get("Accept"));
    }

    @Test
    void importCollection_preservesNestedFolders() throws Exception {
        ObjectNode root = collectionRoot("nested");
        ArrayNode items = (ArrayNode) root.get("item");

        // Top-level request
        items.add(requestItem("top-request", "GET", "http://example.com/top"));

        // Folder containing a request
        ObjectNode folder = mapper.createObjectNode();
        folder.put("name", "Users");
        ArrayNode folderItems = folder.putArray("item");
        folderItems.add(requestItem("nested-request", "POST", "http://example.com/nested"));
        items.add(folder);

        Path file = writeJson(root, "nested.json");
        ImportResult result = importer.importFile(file, null);

        RequestCollection col = result.getCollections().get(0);
        // Top-level: 1 request, 1 folder
        assertEquals(1, col.getRequests().size());
        assertEquals("top-request", col.getRequests().get(0).getName());
        assertEquals(1, col.getFolders().size());
        assertEquals("Users", col.getFolders().get(0).getName());
        assertEquals(1, col.getFolders().get(0).getRequests().size());
        assertEquals("nested-request", col.getFolders().get(0).getRequests().get(0).getName());
    }

    @Test
    void importCollection_inheritsCollectionLevelAuth() throws Exception {
        ObjectNode root = collectionRoot("auth-test");

        // Collection-level bearer auth
        ObjectNode auth = root.putObject("auth");
        auth.put("type", "bearer");
        ArrayNode bearerArr = auth.putArray("bearer");
        bearerArr.addObject().put("key", "token").put("value", "{{collectionToken}}");

        // Request with no auth
        ((ArrayNode) root.get("item")).add(requestItem("secure", "GET", "http://example.com"));

        Path file = writeJson(root, "auth-test.json");
        ImportResult result = importer.importFile(file, null);

        SavedRequest req = result.getCollections().get(0).getRequests().get(0);
        assertNotNull(req.getAuth());
        assertEquals(AuthType.BEARER, req.getAuth().getType());
        assertEquals("{{collectionToken}}", req.getAuth().getToken());
    }

    @Test
    void importCollection_requestAuthOverridesCollectionAuth() throws Exception {
        ObjectNode root = collectionRoot("auth-override");

        // Collection-level bearer
        ObjectNode colAuth = root.putObject("auth");
        colAuth.put("type", "bearer");
        colAuth.putArray("bearer").addObject().put("key", "token").put("value", "col-token");

        // Request with its own basic auth
        ObjectNode item = requestItem("item", "GET", "http://example.com");
        ObjectNode reqAuth = ((ObjectNode) item.get("request")).putObject("auth");
        reqAuth.put("type", "basic");
        ArrayNode basicArr = reqAuth.putArray("basic");
        basicArr.addObject().put("key", "username").put("value", "alice");
        basicArr.addObject().put("key", "password").put("value", "secret");
        ((ArrayNode) root.get("item")).add(item);

        Path file = writeJson(root, "auth-override.json");
        ImportResult result = importer.importFile(file, null);

        SavedRequest req = result.getCollections().get(0).getRequests().get(0);
        assertEquals(AuthType.BASIC, req.getAuth().getType());
        assertEquals("alice", req.getAuth().getUsername());
    }

    @Test
    void importCollection_extractsCollectionVarsAsEnvironment() throws Exception {
        ObjectNode root = collectionRoot("my-col");
        ArrayNode vars = root.putArray("variable");
        vars.addObject().put("key", "baseUrl").put("value", "http://localhost");
        vars.addObject().put("key", "token").put("value", "abc");

        Path file = writeJson(root, "vars.json");
        ImportResult result = importer.importFile(file, null);

        assertEquals(1, result.getEnvironments().size());
        Environment env = result.getEnvironments().get(0);
        assertEquals("my-col-vars", env.getName());
        assertEquals("http://localhost", env.getVariables().get("baseUrl"));
        assertEquals("abc", env.getVariables().get("token"));
    }

    @Test
    void importCollection_noCompanionEnvWhenNoVars() throws Exception {
        Path file = buildCollectionFile("no-vars", null);
        ImportResult result = importer.importFile(file, null);
        assertTrue(result.getEnvironments().isEmpty());
    }

    @Test
    void importCollection_rawBodyMappedCorrectly() throws Exception {
        ObjectNode root = collectionRoot("body-test");
        ObjectNode item = requestItem("post-it", "POST", "http://example.com/data");
        ObjectNode body = ((ObjectNode) item.get("request")).putObject("body");
        body.put("mode", "raw");
        body.put("raw", "{\"key\":\"value\"}");
        body.putObject("options").putObject("raw").put("language", "json");
        ((ArrayNode) root.get("item")).add(item);

        Path file = writeJson(root, "body.json");
        ImportResult result = importer.importFile(file, null);

        SavedRequest req = result.getCollections().get(0).getRequests().get(0);
        assertEquals(BodyType.JSON, req.getBodyType());
        assertEquals("{\"key\":\"value\"}", req.getBody());
    }

    // â”€â”€ Environment import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void importEnvironment_mapsNameAndVariables() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "dev");
        root.put("_postman_variable_scope", "environment");
        ArrayNode values = root.putArray("values");
        values.addObject().put("key", "baseUrl").put("value", "http://localhost:8080").put("enabled", true);
        values.addObject().put("key", "token").put("value", "abc123").put("enabled", true);

        Path file = writeJson(root, "dev-env.json");
        ImportResult result = importer.importFile(file, null);

        assertEquals(0, result.getCollections().size());
        assertEquals(1, result.getEnvironments().size());
        Environment env = result.getEnvironments().get(0);
        assertEquals("dev", env.getName());
        assertEquals("http://localhost:8080", env.getVariables().get("baseUrl"));
    }

    @Test
    void importEnvironment_skipsDisabledVariables() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "env");
        root.put("_postman_variable_scope", "environment");
        ArrayNode values = root.putArray("values");
        values.addObject().put("key", "active").put("value", "yes").put("enabled", true);
        values.addObject().put("key", "inactive").put("value", "no").put("enabled", false);

        Path file = writeJson(root, "env.json");
        ImportResult result = importer.importFile(file, null);

        Environment env = result.getEnvironments().get(0);
        assertTrue(env.getVariables().containsKey("active"));
        assertFalse(env.getVariables().containsKey("inactive"));
    }

    @Test
    void importEnvironment_nameOverrideApplied() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "original");
        root.put("_postman_variable_scope", "environment");
        root.putArray("values");

        Path file = writeJson(root, "env-override.json");
        ImportResult result = importer.importFile(file, "renamed");

        assertEquals("renamed", result.getEnvironments().get(0).getName());
    }

    @Test
    void importFile_throwsOnUnrecognizedFormat() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("unknown", "data");
        Path file = writeJson(root, "unknown.json");

        assertThrows(IOException.class, () -> importer.importFile(file, null));
    }

    // â”€â”€ Builders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Path buildCollectionFile(String name, String nameOverride) throws Exception {
        ObjectNode root = collectionRoot(name);
        ((ArrayNode) root.get("item")).add(requestItem("get-users", "GET", "{{baseUrl}}/users"));
        return writeJson(root, name + ".json");
    }

    private ObjectNode collectionRoot(String name) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("info").put("name", name);
        root.putArray("item");
        return root;
    }

    private ObjectNode requestItem(String name, String method, String url) {
        ObjectNode item = mapper.createObjectNode();
        item.put("name", name);
        ObjectNode request = item.putObject("request");
        request.put("method", method);
        ObjectNode urlNode = request.putObject("url");
        urlNode.put("raw", url);
        ArrayNode headers = request.putArray("header");
        headers.addObject().put("key", "Accept").put("value", "application/json");
        return item;
    }

    private Path writeJson(ObjectNode root, String filename) throws Exception {
        Path file = tempDir.resolve(filename);
        mapper.writeValue(file.toFile(), root);
        return file;
    }
}
