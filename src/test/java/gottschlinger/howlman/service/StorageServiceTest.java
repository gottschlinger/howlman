package gottschlinger.howlman.service;

import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.util.ConfigPaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    StorageService storage;

    @BeforeEach
    void setUp() throws IOException {
        ConfigPaths paths = new ConfigPaths(tempDir);
        storage = new StorageService(paths);
        storage.init();
    }

    // â”€â”€ Config â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void loadConfig_returnsDefaults_whenFileAbsent() throws IOException {
        AppConfig config = storage.loadConfig();
        assertNull(config.getActiveEnvironment());
        assertEquals("default", config.getDefaultCollection());
    }

    @Test
    void saveAndLoadConfig_roundTrips() throws IOException {
        AppConfig config = new AppConfig();
        config.setActiveEnvironment("dev");
        config.setDefaultCollection("my-api");
        storage.saveConfig(config);

        AppConfig loaded = storage.loadConfig();
        assertEquals("dev", loaded.getActiveEnvironment());
        assertEquals("my-api", loaded.getDefaultCollection());
    }

    // â”€â”€ Collections â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void saveAndLoadCollection_roundTrips() throws IOException {
        SavedRequest req = new SavedRequest();
        req.setName("get-users");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://localhost/users");

        RequestCollection col = new RequestCollection("default");
        col.getRequests().add(req);
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        assertEquals("default", loaded.getName());
        assertEquals(1, loaded.getRequests().size());
        assertEquals("get-users", loaded.getRequests().get(0).getName());
        assertEquals(HttpMethod.GET, loaded.getRequests().get(0).getMethod());
    }

    @Test
    void loadCollection_throwsWhenMissing() {
        assertThrows(IOException.class, () -> storage.loadCollection("nonexistent"));
    }

    @Test
    void listCollectionNames_returnsAllSaved() throws IOException {
        storage.saveCollection(new RequestCollection("alpha"));
        storage.saveCollection(new RequestCollection("beta"));

        List<String> names = storage.listCollectionNames();
        assertEquals(List.of("alpha", "beta"), names);
    }

    @Test
    void deleteCollection_removesFile() throws IOException {
        storage.saveCollection(new RequestCollection("temp"));
        storage.deleteCollection("temp");

        assertThrows(IOException.class, () -> storage.loadCollection("temp"));
    }

    @Test
    void deleteCollection_throwsWhenMissing() {
        assertThrows(IOException.class, () -> storage.deleteCollection("ghost"));
    }

    // â”€â”€ Environments â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void saveAndLoadEnvironment_roundTrips() throws IOException {
        Environment env = new Environment("dev");
        env.getVariables().put("baseUrl", "http://localhost:8080");
        env.getVariables().put("token", "abc123");
        storage.saveEnvironment(env);

        Environment loaded = storage.loadEnvironment("dev");
        assertEquals("dev", loaded.getName());
        assertEquals("http://localhost:8080", loaded.getVariables().get("baseUrl"));
        assertEquals("abc123", loaded.getVariables().get("token"));
    }

    @Test
    void loadEnvironment_throwsWhenMissing() {
        assertThrows(IOException.class, () -> storage.loadEnvironment("nonexistent"));
    }

    @Test
    void listEnvironmentNames_returnsAllSaved() throws IOException {
        storage.saveEnvironment(new Environment("dev"));
        storage.saveEnvironment(new Environment("prod"));

        List<String> names = storage.listEnvironmentNames();
        assertEquals(List.of("dev", "prod"), names);
    }

    @Test
    void deleteEnvironment_removesFile() throws IOException {
        storage.saveEnvironment(new Environment("staging"));
        storage.deleteEnvironment("staging");

        assertThrows(IOException.class, () -> storage.loadEnvironment("staging"));
    }

    // â”€â”€ MalformedStorageException â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void loadCollection_throwsMalformedStorageException_onBadJson() throws Exception {
        java.nio.file.Path file = tempDir.resolve("collections").resolve("broken.json");
        java.nio.file.Files.writeString(file, "{ not valid json }");
        assertThrows(MalformedStorageException.class, () -> storage.loadCollection("broken"));
    }

    @Test
    void loadEnvironment_throwsMalformedStorageException_onBadJson() throws Exception {
        java.nio.file.Path file = tempDir.resolve("environments").resolve("broken.json");
        java.nio.file.Files.writeString(file, "{ not valid json }");
        assertThrows(MalformedStorageException.class, () -> storage.loadEnvironment("broken"));
    }

    // â”€â”€ Folders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void addFolder_createsNestedStructure() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "auth");
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        assertEquals(1, loaded.getFolders().size());
        assertEquals("auth", loaded.getFolders().get(0).getName());
    }

    @Test
    void upsertRequest_inFolder_roundTrips() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "users");

        SavedRequest req = new SavedRequest();
        req.setName("list-users");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://localhost/users");
        StorageService.upsertRequest(col, List.of("users"), req);
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        var found = StorageService.findRequest(loaded, List.of("users"), "list-users");
        assertTrue(found.isPresent());
        assertEquals("http://localhost/users", found.get().getUrl());
    }

    @Test
    void upsertRequest_inNestedFolder_roundTrips() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "api");
        StorageService.addFolder(col, List.of("api"), "v1");

        SavedRequest req = new SavedRequest();
        req.setName("ping");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://localhost/api/v1/ping");
        StorageService.upsertRequest(col, List.of("api", "v1"), req);
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        var found = StorageService.findRequest(loaded, List.of("api", "v1"), "ping");
        assertTrue(found.isPresent());
        assertEquals("http://localhost/api/v1/ping", found.get().getUrl());
    }

    @Test
    void deleteRequest_inFolder_removesEntry() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "misc");
        SavedRequest req = new SavedRequest();
        req.setName("to-delete");
        req.setMethod(HttpMethod.DELETE);
        req.setUrl("http://localhost/misc");
        StorageService.upsertRequest(col, List.of("misc"), req);
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        StorageService.deleteRequest(loaded, List.of("misc"), "to-delete");
        storage.saveCollection(loaded);

        RequestCollection reloaded = storage.loadCollection("default");
        assertTrue(StorageService.findRequest(reloaded, List.of("misc"), "to-delete").isEmpty());
    }

    @Test
    void renameFolder_updatesName() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "old-name");
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        StorageService.renameFolder(loaded, List.of("old-name"), "new-name");
        storage.saveCollection(loaded);

        RequestCollection reloaded = storage.loadCollection("default");
        assertEquals("new-name", reloaded.getFolders().get(0).getName());
    }

    @Test
    void deleteFolder_removesFolder() throws IOException {
        RequestCollection col = new RequestCollection("default");
        StorageService.addFolder(col, List.of(), "temp");
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        StorageService.deleteFolder(loaded, List.of("temp"));
        storage.saveCollection(loaded);

        RequestCollection reloaded = storage.loadCollection("default");
        assertTrue(reloaded.getFolders().isEmpty());
    }

    @Test
    void existingCollection_withoutFolders_loadsCleanly() throws IOException {
        // Simulate a pre-folders JSON file that has no "folders" field
        java.nio.file.Path file = tempDir.resolve("collections").resolve("legacy.json");
        java.nio.file.Files.writeString(file,
                "{\"name\":\"legacy\",\"requests\":[{\"name\":\"r1\",\"method\":\"GET\",\"url\":\"http://x\"}]}");

        RequestCollection loaded = storage.loadCollection("legacy");
        assertEquals("legacy", loaded.getName());
        assertEquals(1, loaded.getRequests().size());
        // folders field may be null or empty â€” both are acceptable
        assertTrue(loaded.getFolders() == null || loaded.getFolders().isEmpty());
    }

    // â”€â”€ resolveVariables â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void resolveVariables_returnsEmptyMap_whenEnvNameIsNull() throws IOException {
        Map<String, String> vars = storage.resolveVariables(null);
        assertTrue(vars.isEmpty());
    }

    @Test
    void resolveVariables_returnsVariablesForNamedEnv() throws IOException {
        Environment env = new Environment("dev");
        env.getVariables().put("key", "value");
        storage.saveEnvironment(env);

        Map<String, String> vars = storage.resolveVariables("dev");
        assertEquals("value", vars.get("key"));
    }

    // â”€â”€ AuthConfig round-trip â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void savedRequest_withAuth_roundTrips() throws IOException {
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("{{authToken}}");

        SavedRequest req = new SavedRequest();
        req.setName("secured");
        req.setMethod(HttpMethod.POST);
        req.setUrl("{{baseUrl}}/secure");
        req.setAuth(auth);

        RequestCollection col = new RequestCollection("default");
        col.getRequests().add(req);
        storage.saveCollection(col);

        RequestCollection loaded = storage.loadCollection("default");
        SavedRequest loadedReq = loaded.getRequests().get(0);
        assertEquals(AuthType.BEARER, loadedReq.getAuth().getType());
        assertEquals("{{authToken}}", loadedReq.getAuth().getToken());
    }
}
