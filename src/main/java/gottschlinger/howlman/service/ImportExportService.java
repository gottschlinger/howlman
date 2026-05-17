package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.model.HowlManBundle;
import gottschlinger.howlman.model.RequestCollection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImportExportService {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Returns {@code base} if not already in {@code existing}, otherwise appends
     * "-2", "-3", … until a free name is found.
     */
    public String nextAvailableName(String base, List<String> existing) {
        if (!existing.contains(base)) return base;
        int n = 2;
        while (existing.contains(base + "-" + n)) n++;
        return base + "-" + n;
    }

    /**
     * Export a single named collection, optionally bundling all environments.
     * Format: "HowlMan" (bundled JSON), "Postman" (+ one env file per environment), "Insomnia" (bundled).
     */
    public void exportCollection(StorageService storage, String name, Path outputFile,
                                 String format, boolean includeEnvs) throws IOException {
        RequestCollection collection = storage.loadCollection(name);
        List<Environment> environments = includeEnvs ? loadAllEnvironments(storage) : List.of();
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        switch (format) {
            case "Postman" -> {
                new PostmanExporter().export(collection, outputFile);
                if (!environments.isEmpty() && parent != null) {
                    new PostmanExporter().exportEnvironments(environments, parent, name);
                }
            }
            case "Insomnia" -> new InsomniaExporter().export(collection, environments, outputFile);
            default -> {
                if (!environments.isEmpty()) {
                    HowlManBundle bundle = new HowlManBundle();
                    bundle.setCollection(collection);
                    bundle.setEnvironments(environments);
                    mapper.writeValue(outputFile.toFile(), bundle);
                } else {
                    mapper.writeValue(outputFile.toFile(), collection);
                }
            }
        }
    }

    public void exportCollection(StorageService storage, String name, Path outputFile,
                                 String format) throws IOException {
        exportCollection(storage, name, outputFile, format, false);
    }

    /** Export in native HowlMan format without environments. */
    public void exportCollection(StorageService storage, String name, Path outputFile) throws IOException {
        exportCollection(storage, name, outputFile, "HowlMan", false);
    }

    private List<Environment> loadAllEnvironments(StorageService storage) throws IOException {
        List<Environment> envs = new ArrayList<>();
        for (String envName : storage.listEnvironmentNames()) {
            envs.add(storage.loadEnvironment(envName));
        }
        return envs;
    }

    /**
     * Export all collections to a directory, one file per collection (native format).
     */
    public void exportAll(StorageService storage, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (String name : storage.listCollectionNames()) {
            RequestCollection collection = storage.loadCollection(name);
            mapper.writeValue(outputDir.resolve(name + ".json").toFile(), collection);
        }
    }
}
