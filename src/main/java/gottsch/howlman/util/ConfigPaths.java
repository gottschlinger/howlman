package gottsch.howlman.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigPaths {

    private static final String ROOT_DIR = ".howlman";
    private static final String COLLECTIONS_DIR = "collections";
    private static final String ENVIRONMENTS_DIR = "environments";
    private static final String CONFIG_FILE = "config.json";

    private final Path root;

    public ConfigPaths() {
        this.root = Paths.get(System.getProperty("user.home"), ROOT_DIR);
    }

    public ConfigPaths(Path root) {
        this.root = root;
    }

    public void initDirectories() throws IOException {
        Files.createDirectories(collectionsDir());
        Files.createDirectories(environmentsDir());
    }

    public Path root() {
        return root;
    }

    public Path configFile() {
        return root.resolve(CONFIG_FILE);
    }

    public Path collectionsDir() {
        return root.resolve(COLLECTIONS_DIR);
    }

    public Path environmentsDir() {
        return root.resolve(ENVIRONMENTS_DIR);
    }

    public Path collectionFile(String name) {
        return collectionsDir().resolve(name + ".json");
    }

    public Path environmentFile(String name) {
        return environmentsDir().resolve(name + ".json");
    }
}
