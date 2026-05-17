package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsomniaExporterTest {

    @TempDir Path tempDir;

    ObjectMapper mapper;
    InsomniaExporter exporter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        exporter = new InsomniaExporter();
    }

    // â”€â”€ Format / workspace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_writesExportFormat4() throws IOException {
        exporter.export(collection("api"), out());
        assertEquals(4, read().path("__export_format").asInt());
    }

    @Test
    void export_writesWorkspace() throws IOException {
        exporter.export(collection("myapi"), out());
        JsonNode workspace = resourceOfType(read(), "workspace");
        assertEquals("myapi", workspace.path("name").asText());
        assertEquals("workspace", workspace.path("_type").asText());
    }

    // â”€â”€ Requests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_mapsTopLevelRequest() throws IOException {
        RequestCollection col = collection("api");
        col.setRequests(List.of(request("get-users", HttpMethod.GET, "https://example.com/users")));

        exporter.export(col, out());
        JsonNode root = read();
        JsonNode workspace = resourceOfType(root, "workspace");
        JsonNode req = resourceOfType(root, "request");

        assertEquals("get-users", req.path("name").asText());
        assertEquals("GET", req.path("method").asText());
        assertEquals("https://example.com/users", req.path("url").asText());
        assertEquals(workspace.path("_id").asText(), req.path("parentId").asText());
    }

    @Test
    void export_mapsHeaders() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.GET, "https://example.com");
        req.setHeaders(Map.of("Authorization", "Bearer tok"));
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode header = resourceOfType(read(), "request").path("headers").get(0);

        assertEquals("Authorization", header.path("name").asText());
        assertEquals("Bearer tok", header.path("value").asText());
    }

    @Test
    void export_mapsBearerAuth() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.GET, "https://example.com");
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("{{myToken}}");
        req.setAuth(auth);
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode authNode = resourceOfType(read(), "request").path("authentication");

        assertEquals("bearer", authNode.path("type").asText());
        assertEquals("{{myToken}}", authNode.path("token").asText());
    }

    @Test
    void export_mapsBasicAuth() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.POST, "https://example.com");
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BASIC);
        auth.setUsername("alice");
        auth.setPassword("secret");
        req.setAuth(auth);
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode authNode = resourceOfType(read(), "request").path("authentication");

        assertEquals("basic", authNode.path("type").asText());
        assertEquals("alice", authNode.path("username").asText());
        assertEquals("secret", authNode.path("password").asText());
    }

    @Test
    void export_mapsJsonBody() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.POST, "https://example.com");
        req.setBodyType(BodyType.JSON);
        req.setBody("{\"name\":\"test\"}");
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode body = resourceOfType(read(), "request").path("body");

        assertEquals("application/json", body.path("mimeType").asText());
        assertEquals("{\"name\":\"test\"}", body.path("text").asText());
    }

    // â”€â”€ Folders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_preservesFolderStructure() throws IOException {
        RequestCollection col = collection("api");
        RequestFolder folder = new RequestFolder();
        folder.setName("users");
        folder.setRequests(List.of(request("list", HttpMethod.GET, "https://example.com/users")));
        col.setFolders(List.of(folder));

        exporter.export(col, out());
        JsonNode root = read();
        JsonNode workspace = resourceOfType(root, "workspace");
        JsonNode group = resourceOfType(root, "request_group");
        JsonNode req = resourceOfType(root, "request");

        assertEquals("users", group.path("name").asText());
        assertEquals(workspace.path("_id").asText(), group.path("parentId").asText());
        assertEquals(group.path("_id").asText(), req.path("parentId").asText());
    }

    @Test
    void export_preservesNestedFolders() throws IOException {
        RequestCollection col = collection("api");

        RequestFolder inner = new RequestFolder();
        inner.setName("v1");
        inner.setRequests(List.of(request("ping", HttpMethod.GET, "https://example.com/v1/ping")));

        RequestFolder outer = new RequestFolder();
        outer.setName("endpoints");
        outer.setFolders(List.of(inner));
        col.setFolders(List.of(outer));

        exporter.export(col, out());
        JsonNode root = read();

        long groupCount = 0;
        for (JsonNode r : root.path("resources")) {
            if ("request_group".equals(r.path("_type").asText())) groupCount++;
        }
        assertEquals(2, groupCount);
    }

    // â”€â”€ Environments â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_includesEnvironments() throws IOException {
        RequestCollection col = collection("api");
        Environment env = new Environment("dev");
        env.setVariables(Map.of("baseUrl", "http://localhost:8080", "token", "abc123"));

        exporter.export(col, List.of(env), out());
        JsonNode root = read();
        JsonNode envNode = resourceOfType(root, "environment");

        assertEquals("dev", envNode.path("name").asText());
        assertEquals("http://localhost:8080", envNode.path("data").path("baseUrl").asText());
        assertEquals("abc123", envNode.path("data").path("token").asText());
    }

    @Test
    void export_environmentParentIsWorkspace() throws IOException {
        RequestCollection col = collection("api");
        Environment env = new Environment("staging");
        env.setVariables(Map.of("k", "v"));

        exporter.export(col, List.of(env), out());
        JsonNode root = read();
        String workspaceId = resourceOfType(root, "workspace").path("_id").asText();
        String envParentId = resourceOfType(root, "environment").path("parentId").asText();

        assertEquals(workspaceId, envParentId);
    }

    @Test
    void export_noEnvironments_omitsEnvironmentResources() throws IOException {
        exporter.export(collection("api"), out());
        for (JsonNode r : read().path("resources")) {
            assertNotEquals("environment", r.path("_type").asText());
        }
    }

    // â”€â”€ Builders / helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Path out() { return tempDir.resolve("out.json"); }

    private JsonNode read() throws IOException {
        return mapper.readTree(out().toFile());
    }

    private RequestCollection collection(String name) {
        RequestCollection col = new RequestCollection();
        col.setName(name);
        return col;
    }

    private SavedRequest request(String name, HttpMethod method, String url) {
        SavedRequest req = new SavedRequest();
        req.setName(name);
        req.setMethod(method);
        req.setUrl(url);
        return req;
    }

    private JsonNode resourceOfType(JsonNode root, String type) {
        for (JsonNode r : root.path("resources")) {
            if (type.equals(r.path("_type").asText())) return r;
        }
        fail("No resource of type '" + type + "' found");
        return null;
    }
}
