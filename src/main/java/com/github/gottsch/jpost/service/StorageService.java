package com.github.gottsch.jpost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.gottsch.jpost.model.AppConfig;
import com.github.gottsch.jpost.model.Collection;
import com.github.gottsch.jpost.model.Environment;
import com.github.gottsch.jpost.util.ConfigPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    public Collection loadCollection(String name) throws IOException {
        Path file = paths.collectionFile(name);
        if (!Files.exists(file)) {
            throw new IOException("Collection not found: " + name);
        }
        return readJson(file, Collection.class);
    }

    public void saveCollection(Collection collection) throws IOException {
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
