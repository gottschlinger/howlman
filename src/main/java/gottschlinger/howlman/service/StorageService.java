package gottschlinger.howlman.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gottschlinger.howlman.model.AppConfig;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.RequestFolder;
import gottschlinger.howlman.model.SavedRequest;
import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.util.ConfigPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageService {

    private final ConfigPaths paths;
    private final ObjectMapper mapper;

    public StorageService(ConfigPaths paths) {
        this.paths = paths;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void init() throws IOException {
        paths.initDirectories();
    }

    // ── Config ──────────────────────────────────────────────────────────────

    public AppConfig loadConfig() throws IOException {
        Path file = paths.configFile();
        if (!Files.exists(file)) {
            return new AppConfig();
        }
        return readJson(file, AppConfig.class);
    }

    public void saveConfig(AppConfig config) throws IOException {
        mapper.writeValue(paths.configFile().toFile(), config);
    }

    // ── Collections ─────────────────────────────────────────────────────────

    public RequestCollection loadCollection(String name) throws IOException {
        Path file = paths.collectionFile(name);
        if (!Files.exists(file)) {
            throw new IOException("Collection not found: " + name);
        }
        return readJson(file, RequestCollection.class);
    }

    public void saveCollection(RequestCollection collection) throws IOException {
        mapper.writeValue(paths.collectionFile(collection.getName()).toFile(), collection);
    }

    public List<String> listCollectionNames() throws IOException {
        return listJsonFileNames(paths.collectionsDir());
    }

    public void deleteCollection(String name) throws IOException {
        Path file = paths.collectionFile(name);
        if (!Files.exists(file)) {
            throw new IOException("Collection not found: " + name);
        }
        Files.delete(file);
    }

    // ── Environments ─────────────────────────────────────────────────────────

    public Environment loadEnvironment(String name) throws IOException {
        Path file = paths.environmentFile(name);
        if (!Files.exists(file)) {
            throw new IOException("Environment not found: " + name);
        }
        return readJson(file, Environment.class);
    }

    public void saveEnvironment(Environment environment) throws IOException {
        mapper.writeValue(paths.environmentFile(environment.getName()).toFile(), environment);
    }

    public List<String> listEnvironmentNames() throws IOException {
        return listJsonFileNames(paths.environmentsDir());
    }

    public void deleteEnvironment(String name) throws IOException {
        Path file = paths.environmentFile(name);
        if (!Files.exists(file)) {
            throw new IOException("Environment not found: " + name);
        }
        Files.delete(file);
    }

    // ── Convenience ──────────────────────────────────────────────────────────

    public Map<String, String> resolveVariables(String envName) throws IOException {
        if (envName == null) {
            return Collections.emptyMap();
        }
        return loadEnvironment(envName).getVariables();
    }

    // ── Folder / request navigation (static helpers) ─────────────────────────

    /**
     * Navigates the folder hierarchy and returns the folder at the given path,
     * or null if the path is empty (representing the collection top-level).
     */
    public static RequestFolder resolveFolder(RequestCollection col, List<String> path) {
        if (path == null || path.isEmpty()) return null;
        List<RequestFolder> current = col.getFolders();
        RequestFolder folder = null;
        for (String segment : path) {
            if (current == null) return null;
            folder = current.stream().filter(f -> f.getName().equals(segment)).findFirst().orElse(null);
            if (folder == null) return null;
            current = folder.getFolders();
        }
        return folder;
    }

    /** A request found at a specific folder location within a collection. */
    public record LocatedRequest(List<String> folderPath, SavedRequest request) {}

    /**
     * Searches every folder in the collection for a request with the given name.
     * Returns all matches (empty = not found, size > 1 = ambiguous).
     */
    public static List<LocatedRequest> findRequestAnywhere(RequestCollection col, String name) {
        List<LocatedRequest> results = new ArrayList<>();
        scanRequests(col.getRequests(), List.of(), name, results);
        scanFolders(col.getFolders(), List.of(), name, results);
        return results;
    }

    private static void scanRequests(List<SavedRequest> requests, List<String> path,
                                     String name, List<LocatedRequest> out) {
        if (requests == null) return;
        for (SavedRequest r : requests) {
            if (name.equals(r.getName())) out.add(new LocatedRequest(path, r));
        }
    }

    private static void scanFolders(List<RequestFolder> folders, List<String> path,
                                    String name, List<LocatedRequest> out) {
        if (folders == null) return;
        for (RequestFolder folder : folders) {
            List<String> sub = new ArrayList<>(path);
            sub.add(folder.getName());
            List<String> subPath = List.copyOf(sub);
            scanRequests(folder.getRequests(), subPath, name, out);
            scanFolders(folder.getFolders(), subPath, name, out);
        }
    }

    public static Optional<SavedRequest> findRequest(RequestCollection col, List<String> folderPath, String name) {
        List<SavedRequest> requests = requestListAt(col, folderPath);
        if (requests == null) return Optional.empty();
        return requests.stream().filter(r -> r.getName().equals(name)).findFirst();
    }

    public static void upsertRequest(RequestCollection col, List<String> folderPath, SavedRequest req) {
        List<SavedRequest> requests = requestListAt(col, folderPath);
        if (requests == null) return;
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).getName().equals(req.getName())) {
                requests.set(i, req);
                return;
            }
        }
        requests.add(req);
    }

    public static boolean deleteRequest(RequestCollection col, List<String> folderPath, String name) {
        List<SavedRequest> requests = requestListAt(col, folderPath);
        if (requests == null) return false;
        return requests.removeIf(r -> r.getName().equals(name));
    }

    public static boolean renameRequest(RequestCollection col, List<String> folderPath, String oldName, String newName) {
        List<SavedRequest> requests = requestListAt(col, folderPath);
        if (requests == null) return false;
        for (SavedRequest req : requests) {
            if (req.getName().equals(oldName)) {
                req.setName(newName);
                return true;
            }
        }
        return false;
    }

    /** Ensures every folder segment in {@code path} exists, creating any that are missing. */
    public static void ensureFolderPath(RequestCollection col, List<String> path) {
        if (path == null || path.isEmpty()) return;
        List<RequestFolder> current = col.getFolders();
        for (String segment : path) {
            RequestFolder folder = current.stream()
                    .filter(f -> f.getName().equals(segment))
                    .findFirst()
                    .orElse(null);
            if (folder == null) {
                folder = new RequestFolder(segment);
                current.add(folder);
            }
            current = folder.getFolders();
        }
    }

    /** Returns all folder paths in the collection, depth-first. First entry is always {@code List.of()} (top level). */
    public static List<List<String>> collectFolderPaths(RequestCollection col) {
        List<List<String>> result = new ArrayList<>();
        result.add(List.of());
        collectFolderPathsRecursive(col.getFolders(), List.of(), result);
        return result;
    }

    private static void collectFolderPathsRecursive(List<RequestFolder> folders,
                                                    List<String> parentPath,
                                                    List<List<String>> result) {
        if (folders == null) return;
        for (RequestFolder folder : folders) {
            List<String> path = new ArrayList<>(parentPath);
            path.add(folder.getName());
            List<String> immutable = List.copyOf(path);
            result.add(immutable);
            collectFolderPathsRecursive(folder.getFolders(), immutable, result);
        }
    }

    public static RequestFolder addFolder(RequestCollection col, List<String> parentPath, String name) {
        List<RequestFolder> siblings = folderListAt(col, parentPath);
        if (siblings == null) return null;
        RequestFolder newFolder = new RequestFolder(name);
        siblings.add(newFolder);
        return newFolder;
    }

    public static boolean renameFolder(RequestCollection col, List<String> path, String newName) {
        if (path == null || path.isEmpty()) return false;
        List<RequestFolder> siblings = folderListAt(col, path.subList(0, path.size() - 1));
        if (siblings == null) return false;
        String target = path.get(path.size() - 1);
        for (RequestFolder f : siblings) {
            if (f.getName().equals(target)) { f.setName(newName); return true; }
        }
        return false;
    }

    public static boolean deleteFolder(RequestCollection col, List<String> path) {
        if (path == null || path.isEmpty()) return false;
        List<RequestFolder> siblings = folderListAt(col, path.subList(0, path.size() - 1));
        if (siblings == null) return false;
        String target = path.get(path.size() - 1);
        return siblings.removeIf(f -> f.getName().equals(target));
    }

    private static List<SavedRequest> requestListAt(RequestCollection col, List<String> folderPath) {
        if (folderPath == null || folderPath.isEmpty()) return col.getRequests();
        RequestFolder folder = resolveFolder(col, folderPath);
        return folder != null ? folder.getRequests() : null;
    }

    private static List<RequestFolder> folderListAt(RequestCollection col, List<String> parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return col.getFolders();
        RequestFolder parent = resolveFolder(col, parentPath);
        return parent != null ? parent.getFolders() : null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private <T> T readJson(Path file, Class<T> type) throws IOException {
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (JsonProcessingException e) {
            throw new MalformedStorageException(file.toString(), e);
        }
    }

    private List<String> listJsonFileNames(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        String filename = p.getFileName().toString();
                        return filename.substring(0, filename.length() - ".json".length());
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
