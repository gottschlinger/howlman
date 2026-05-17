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

class PostmanExporterTest {

    @TempDir Path tempDir;

    ObjectMapper mapper;
    PostmanExporter exporter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        exporter = new PostmanExporter();
    }

    // â”€â”€ Collection info â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_writesCollectionInfo() throws IOException {
        exporter.export(collection("myapi"), out());
        JsonNode root = read();
        assertEquals("myapi", root.path("info").path("name").asText());
        assertTrue(root.path("info").path("schema").asText().contains("v2.1.0"));
    }

    // â”€â”€ Requests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_mapsTopLevelRequest() throws IOException {
        RequestCollection col = collection("api");
        col.setRequests(List.of(request("get-users", HttpMethod.GET, "https://example.com/users")));

        exporter.export(col, out());
        JsonNode item = read().path("item").get(0);

        assertEquals("get-users", item.path("name").asText());
        assertEquals("GET", item.path("request").path("method").asText());
        assertEquals("https://example.com/users", item.path("request").path("url").asText());
    }

    @Test
    void export_mapsHeaders() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.GET, "https://example.com");
        req.setHeaders(Map.of("X-Key", "myvalue"));
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode header = read().path("item").get(0).path("request").path("header").get(0);

        assertEquals("X-Key", header.path("key").asText());
        assertEquals("myvalue", header.path("value").asText());
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
        JsonNode authNode = read().path("item").get(0).path("request").path("auth");

        assertEquals("bearer", authNode.path("type").asText());
        assertEquals("{{myToken}}", authNode.path("bearer").get(0).path("value").asText());
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
        JsonNode authNode = read().path("item").get(0).path("request").path("auth");

        assertEquals("basic", authNode.path("type").asText());
        boolean hasUsername = false;
        for (JsonNode entry : authNode.path("basic")) {
            if ("username".equals(entry.path("key").asText())) {
                assertEquals("alice", entry.path("value").asText());
                hasUsername = true;
            }
        }
        assertTrue(hasUsername);
    }

    @Test
    void export_mapsJsonBody() throws IOException {
        RequestCollection col = collection("api");
        SavedRequest req = request("r", HttpMethod.POST, "https://example.com");
        req.setBodyType(BodyType.JSON);
        req.setBody("{\"name\":\"test\"}");
        col.setRequests(List.of(req));

        exporter.export(col, out());
        JsonNode body = read().path("item").get(0).path("request").path("body");

        assertEquals("raw", body.path("mode").asText());
        assertEquals("{\"name\":\"test\"}", body.path("raw").asText());
        assertEquals("json", body.path("options").path("raw").path("language").asText());
    }

    // â”€â”€ Folders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void export_preservesFolderStructure() throws IOException {
        RequestCollection col = collection("api");
        RequestFolder folder = new RequestFolder();
        folder.setName("auth");
        folder.setRequests(List.of(request("login", HttpMethod.POST, "https://example.com/login")));
        col.setFolders(List.of(folder));

        exporter.export(col, out());
        JsonNode folderNode = read().path("item").get(0);

        assertEquals("auth", folderNode.path("name").asText());
        assertEquals("login", folderNode.path("item").get(0).path("name").asText());
    }

    @Test
    void export_preservesNestedFolders() throws IOException {
        RequestCollection col = collection("api");

        RequestFolder inner = new RequestFolder();
        inner.setName("v1");
        inner.setRequests(List.of(request("ping", HttpMethod.GET, "https://example.com/v1/ping")));

        RequestFolder outer = new RequestFolder();
        outer.setName("api");
        outer.setFolders(List.of(inner));
        col.setFolders(List.of(outer));

        exporter.export(col, out());
        JsonNode outerNode = read().path("item").get(0);
        JsonNode innerNode = outerNode.path("item").get(0);

        assertEquals("api", outerNode.path("name").asText());
        assertEquals("v1", innerNode.path("name").asText());
        assertEquals("ping", innerNode.path("item").get(0).path("name").asText());
    }

    // â”€â”€ Environment export â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void exportEnvironments_writesOneFilePerEnvironment() throws IOException {
        Environment dev = new Environment("dev");
        dev.setVariables(Map.of("baseUrl", "http://localhost"));
        Environment prod = new Environment("prod");
        prod.setVariables(Map.of("baseUrl", "https://api.example.com"));

        exporter.exportEnvironments(List.of(dev, prod), tempDir, "myapi");

        assertTrue(tempDir.resolve("postman_myapi_env_dev.json").toFile().exists());
        assertTrue(tempDir.resolve("postman_myapi_env_prod.json").toFile().exists());
    }

    @Test
    void exportEnvironments_writesValuesArray() throws IOException {
        Environment env = new Environment("dev");
        env.setVariables(Map.of("baseUrl", "http://localhost:8080"));

        exporter.exportEnvironments(List.of(env), tempDir, "myapi");

        JsonNode root = mapper.readTree(tempDir.resolve("postman_myapi_env_dev.json").toFile());
        assertEquals("dev", root.path("name").asText());
        assertEquals("environment", root.path("_postman_variable_scope").asText());
        JsonNode values = root.path("values");
        assertEquals(1, values.size());
        assertEquals("baseUrl", values.get(0).path("key").asText());
        assertEquals("http://localhost:8080", values.get(0).path("value").asText());
        assertTrue(values.get(0).path("enabled").asBoolean());
    }

    // â”€â”€ Builders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
}
