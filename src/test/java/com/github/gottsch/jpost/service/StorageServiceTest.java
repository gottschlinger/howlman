package com.github.gottsch.jpost.service;

import com.github.gottsch.jpost.model.*;
import com.github.gottsch.jpost.service.MalformedStorageException;
import com.github.gottsch.jpost.util.ConfigPaths;
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

    // ── Config ───────────────────────────────────────────────────────────────

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

    // ── Collections ──────────────────────────────────────────────────────────

    @Test
    void saveAndLoadCollection_roundTrips() throws IOException {
        SavedRequest req = new SavedRequest();
        req.setName("get-users");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://localhost/users");

        Collection col = new Collection("default");
        col.getRequests().add(req);
        storage.saveCollection(col);

        Collection loaded = storage.loadCollection("default");
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
        storage.saveCollection(new Collection("alpha"));
        storage.saveCollection(new Collection("beta"));

        List<String> names = storage.listCollectionNames();
        assertEquals(List.of("alpha", "beta"), names);
    }

    @Test
    void deleteCollection_removesFile() throws IOException {
        storage.saveCollection(new Collection("temp"));
        storage.deleteCollection("temp");

        assertThrows(IOException.class, () -> storage.loadCollection("temp"));
    }

    @Test
    void deleteCollection_throwsWhenMissing() {
        assertThrows(IOException.class, () -> storage.deleteCollection("ghost"));
    }

    // ── Environments ─────────────────────────────────────────────────────────

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

    // ── MalformedStorageException ─────────────────────────────────────────────

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

    // ── resolveVariables ─────────────────────────────────────────────────────

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

    // ── AuthConfig round-trip ─────────────────────────────────────────────────

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

        Collection col = new Collection("default");
        col.getRequests().add(req);
        storage.saveCollection(col);

        Collection loaded = storage.loadCollection("default");
        SavedRequest loadedReq = loaded.getRequests().get(0);
        assertEquals(AuthType.BEARER, loadedReq.getAuth().getType());
        assertEquals("{{authToken}}", loadedReq.getAuth().getToken());
    }
}
