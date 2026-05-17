package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InsomniaImporterTest {

    @TempDir
    Path tempDir;

    ObjectMapper mapper;
    InsomniaImporter importer;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        importer = new InsomniaImporter();
    }

    // â”€â”€ Detection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void isInsomnia_detectsExportFormat4() {
        ObjectNode root = mapper.createObjectNode();
        root.put("__export_format", 4);
        root.putArray("resources");
        assertTrue(importer.isInsomnia(root));
    }

    @Test
    void isInsomnia_returnsFalseForOtherFormats() {
        ObjectNode root = mapper.createObjectNode();
        root.put("__export_format", 3);
        assertFalse(importer.isInsomnia(root));

        assertFalse(importer.isInsomnia(mapper.createObjectNode()));
    }

    // â”€â”€ Collection import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void import_createsOneCollectionPerWorkspace() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");

        resources.add(workspace("wrk_1", "First API"));
        resources.add(workspace("wrk_2", "Second API"));
        resources.add(request("req_1", "wrk_1", "GET", "http://example.com/a"));
        resources.add(request("req_2", "wrk_2", "POST", "http://example.com/b"));

        ImportResult result = importer.parse(root, null);

        assertEquals(2, result.getCollections().size());
        assertTrue(result.getCollections().stream().anyMatch(c -> c.getName().equals("First API")));
        assertTrue(result.getCollections().stream().anyMatch(c -> c.getName().equals("Second API")));
    }

    @Test
    void import_mapsRequestFields() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "My API"));
        resources.add(request("req_1", "wrk_1", "DELETE", "http://example.com/item/1"));

        ImportResult result = importer.parse(root, null);

        SavedRequest req = result.getCollections().get(0).getRequests().get(0);
        assertEquals(HttpMethod.DELETE, req.getMethod());
        assertEquals("http://example.com/item/1", req.getUrl());
    }

    @Test
    void import_normalizesInsomniaVariableSpaces() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "vars"));
        ObjectNode req = request("req_1", "wrk_1", "GET", "{{ base_url }}/api");
        // Add a header with spaced var (Insomnia uses array of {name, value})
        req.withArray("headers").addObject()
                .put("name", "Authorization")
                .put("value", "Bearer {{ token }}");
        resources.add(req);

        ImportResult result = importer.parse(root, null);

        SavedRequest savedReq = result.getCollections().get(0).getRequests().get(0);
        assertEquals("{{base_url}}/api", savedReq.getUrl());
    }

    @Test
    void import_preservesRequestGroupsAsFolders() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "API"));

        // Top-level request
        resources.add(request("req_top", "wrk_1", "GET", "http://example.com/health"));

        // A request group (folder) under the workspace
        ObjectNode group = mapper.createObjectNode();
        group.put("_id", "grp_1");
        group.put("_type", "request_group");
        group.put("parentId", "wrk_1");
        group.put("name", "Users");
        resources.add(group);

        // A request inside the group
        resources.add(request("req_1", "grp_1", "GET", "http://example.com/users"));

        ImportResult result = importer.parse(root, null);

        RequestCollection col = result.getCollections().get(0);
        assertEquals(1, col.getRequests().size());
        assertEquals("http://example.com/health", col.getRequests().get(0).getUrl());
        assertEquals(1, col.getFolders().size());
        assertEquals("Users", col.getFolders().get(0).getName());
        assertEquals(1, col.getFolders().get(0).getRequests().size());
        assertEquals("http://example.com/users", col.getFolders().get(0).getRequests().get(0).getUrl());
    }

    @Test
    void import_preservesNestedRequestGroups() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "API"));

        ObjectNode outer = mapper.createObjectNode();
        outer.put("_id", "grp_outer");
        outer.put("_type", "request_group");
        outer.put("parentId", "wrk_1");
        outer.put("name", "api");
        resources.add(outer);

        ObjectNode inner = mapper.createObjectNode();
        inner.put("_id", "grp_inner");
        inner.put("_type", "request_group");
        inner.put("parentId", "grp_outer");
        inner.put("name", "v1");
        resources.add(inner);

        resources.add(request("req_1", "grp_inner", "GET", "http://example.com/api/v1/ping"));

        ImportResult result = importer.parse(root, null);

        RequestCollection col = result.getCollections().get(0);
        assertEquals(0, col.getRequests().size());
        assertEquals(1, col.getFolders().size());
        assertEquals("api", col.getFolders().get(0).getName());
        assertEquals(1, col.getFolders().get(0).getFolders().size());
        assertEquals("v1", col.getFolders().get(0).getFolders().get(0).getName());
        assertEquals(1, col.getFolders().get(0).getFolders().get(0).getRequests().size());
    }

    @Test
    void import_nameOverrideAppliedToSingleWorkspace() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "Original Name"));
        resources.add(request("req_1", "wrk_1", "GET", "http://example.com"));

        ImportResult result = importer.parse(root, "override-name");

        assertEquals("override-name", result.getCollections().get(0).getName());
    }

    // â”€â”€ Environment import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void import_importsEnvironments() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "My API"));

        ObjectNode env = mapper.createObjectNode();
        env.put("_id", "env_1");
        env.put("_type", "environment");
        env.put("parentId", "wrk_1");
        env.put("name", "dev");
        ObjectNode data = env.putObject("data");
        data.put("baseUrl", "http://localhost:8080");
        data.put("token", "abc123");
        resources.add(env);

        ImportResult result = importer.parse(root, null);

        assertEquals(1, result.getEnvironments().size());
        Environment savedEnv = result.getEnvironments().get(0);
        assertEquals("dev", savedEnv.getName());
        assertEquals("http://localhost:8080", savedEnv.getVariables().get("baseUrl"));
    }

    @Test
    void import_baseEnvironmentRenamedToWorkspaceName() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "My API"));

        ObjectNode env = mapper.createObjectNode();
        env.put("_id", "env_1");
        env.put("_type", "environment");
        env.put("parentId", "wrk_1");
        env.put("name", "Base Environment");
        env.putObject("data").put("key", "val");
        resources.add(env);

        ImportResult result = importer.parse(root, null);

        assertEquals("My API", result.getEnvironments().get(0).getName());
    }

    // â”€â”€ Auth / Body â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void import_mapsBearerAuth() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "api"));

        ObjectNode req = request("req_1", "wrk_1", "GET", "http://example.com");
        ObjectNode auth = req.putObject("authentication");
        auth.put("type", "bearer");
        auth.put("token", "{{myToken}}");
        resources.add(req);

        ImportResult result = importer.parse(root, null);

        SavedRequest savedReq = result.getCollections().get(0).getRequests().get(0);
        assertNotNull(savedReq.getAuth());
        assertEquals(AuthType.BEARER, savedReq.getAuth().getType());
        assertEquals("{{myToken}}", savedReq.getAuth().getToken());
    }

    @Test
    void import_mapsJsonBody() {
        ObjectNode root = exportRoot();
        ArrayNode resources = (ArrayNode) root.get("resources");
        resources.add(workspace("wrk_1", "api"));

        ObjectNode req = request("req_1", "wrk_1", "POST", "http://example.com");
        ObjectNode body = req.putObject("body");
        body.put("mimeType", "application/json");
        body.put("text", "{\"name\":\"test\"}");
        resources.add(req);

        ImportResult result = importer.parse(root, null);

        SavedRequest savedReq = result.getCollections().get(0).getRequests().get(0);
        assertEquals(BodyType.JSON, savedReq.getBodyType());
        assertEquals("{\"name\":\"test\"}", savedReq.getBody());
    }

    // â”€â”€ Builders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ObjectNode exportRoot() {
        ObjectNode root = mapper.createObjectNode();
        root.put("__export_format", 4);
        root.putArray("resources");
        return root;
    }

    private ObjectNode workspace(String id, String name) {
        ObjectNode n = mapper.createObjectNode();
        n.put("_id", id);
        n.put("_type", "workspace");
        n.put("name", name);
        return n;
    }

    private ObjectNode request(String id, String parentId, String method, String url) {
        ObjectNode n = mapper.createObjectNode();
        n.put("_id", id);
        n.put("_type", "request");
        n.put("parentId", parentId);
        n.put("name", id);
        n.put("method", method);
        n.put("url", url);
        n.putArray("headers");
        return n;
    }
}
