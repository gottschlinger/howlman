package gottsch.howlman.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gottsch.howlman.HowlMan;
import gottsch.howlman.model.*;
import gottsch.howlman.service.*;
import gottsch.howlman.service.MalformedStorageException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "request",
        mixinStandardHelpOptions = true,
        description = "Manage saved requests",
        subcommands = {
                RequestCommand.SaveCommand.class,
                RequestCommand.RunCommand.class,
                RequestCommand.ListCommand.class,
                RequestCommand.ShowCommand.class,
                RequestCommand.RemoveCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class RequestCommand implements Callable<Integer> {

    @ParentCommand HowlMan parent;

    StorageService storage() { return parent.storage; }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    private String resolveCollection(String option) throws IOException {
        if (option != null) return option;
        return storage().loadConfig().getDefaultCollection();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Command(name = "save", description = "Save a request to a collection")
    static class SaveCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Option(names = "--name", required = true, description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection name (default: from config)")
        String collection;

        @Option(names = {"--method", "-X"}, description = "HTTP method (default: GET)")
        HttpMethod method = HttpMethod.GET;

        @Option(names = "--url", required = true, description = "URL (supports {{variables}})")
        String url;

        @Option(names = {"--header", "-H"}, description = "Header in Key:Value format (repeatable)")
        List<String> headers;

        @Option(names = "--body", description = "Request body")
        String body;

        @Option(names = "--body-type", description = "Body type: json, form, raw (default: none)")
        BodyType bodyType = BodyType.NONE;

        @Option(names = "--auth-type", description = "Auth type: none, bearer, basic (default: none)")
        AuthType authType = AuthType.NONE;

        @Option(names = "--token", description = "Bearer token")
        String token;

        @Option(names = "--username", description = "Basic auth username")
        String username;

        @Option(names = "--password", description = "Basic auth password")
        String password;

        @Override
        public Integer call() {
            try {
                String colName = req.resolveCollection(collection);
                Collection col = loadOrCreateCollection(colName);

                SavedRequest saved = buildRequest();

                // overwrite if same name exists
                col.getRequests().removeIf(r -> r.getName().equals(name));
                col.getRequests().add(saved);
                req.storage().saveCollection(col);

                System.out.println("Saved request '" + name + "' to collection: " + colName);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private Collection loadOrCreateCollection(String colName) throws IOException {
            try {
                return req.storage().loadCollection(colName);
            } catch (IOException e) {
                Collection col = new Collection(colName);
                req.storage().saveCollection(col);
                return col;
            }
        }

        SavedRequest buildRequest() {
            SavedRequest r = new SavedRequest();
            r.setName(name);
            r.setMethod(method);
            r.setUrl(url);
            r.setBody(body);
            r.setBodyType(bodyType);

            if (headers != null && !headers.isEmpty()) {
                Map<String, String> headerMap = new LinkedHashMap<>();
                for (String header : headers) {
                    int colon = header.indexOf(':');
                    if (colon < 1) {
                        System.err.println("Warning: skipping malformed header: " + header);
                        continue;
                    }
                    headerMap.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
                }
                r.setHeaders(headerMap);
            }

            if (authType != AuthType.NONE) {
                AuthConfig auth = new AuthConfig();
                auth.setType(authType);
                auth.setToken(token);
                auth.setUsername(username);
                auth.setPassword(password);
                r.setAuth(auth);
            }

            return r;
        }
    }

    // ── run ──────────────────────────────────────────────────────────────────

    @Command(name = "run", description = "Run a saved request")
    static class RunCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection name (default: from config)")
        String collection;

        @Option(names = "--env", description = "Environment name (overrides active environment)")
        String env;

        @Option(names = "--curl", description = "Print curl command instead of executing")
        boolean curlMode;

        @Option(names = "--verbose", description = "Print response headers")
        boolean verbose;

        @Override
        public Integer call() {
            SavedRequest resolved;
            try {
                String colName = req.resolveCollection(collection);
                Collection col = req.storage().loadCollection(colName);

                SavedRequest saved = col.getRequests().stream()
                        .filter(r -> r.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new IOException(
                                "Request not found: '" + name + "' in collection: " + colName));

                String envName = env != null ? env : req.storage().loadConfig().getActiveEnvironment();
                Map<String, String> variables = req.storage().resolveVariables(envName);
                resolved = new InterpolationService().interpolate(saved, variables);
                InterpolationService.warnUnresolved(resolved);
            } catch (MalformedStorageException e) {
                System.err.println("Error: " + e.getMessage());
                return 3;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }

            if (curlMode) {
                System.out.println(new CurlGenerator().generate(resolved));
                return 0;
            }

            try {
                var response = new HttpService().execute(resolved);
                new ResponsePrinter().print(response, verbose);
                return response.statusCode() < 400 ? 0 : 1;
            } catch (Exception e) {
                System.err.println("Error: connection failed: " + e.getMessage());
                return 2;
            }
        }
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Command(name = "list", description = "List saved requests")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Option(names = "--collection", description = "Collection name (default: all collections)")
        String collection;

        @Override
        public Integer call() {
            try {
                List<String> colNames = collection != null
                        ? List.of(collection)
                        : req.storage().listCollectionNames();

                boolean any = false;
                for (String colName : colNames) {
                    try {
                        Collection col = req.storage().loadCollection(colName);
                        for (SavedRequest r : col.getRequests()) {
                            System.out.printf("[%s] %-20s %-7s %s%n",
                                    colName, r.getName(), r.getMethod(), r.getUrl());
                            any = true;
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: could not load collection '" + colName + "': " + e.getMessage());
                    }
                }
                if (!any) System.out.println("No saved requests found.");
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── show ─────────────────────────────────────────────────────────────────

    @Command(name = "show", description = "Show a saved request as JSON")
    static class ShowCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection name (default: from config)")
        String collection;

        private static final ObjectMapper MAPPER = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        @Override
        public Integer call() {
            try {
                String colName = req.resolveCollection(collection);
                Collection col = req.storage().loadCollection(colName);

                SavedRequest saved = col.getRequests().stream()
                        .filter(r -> r.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new IOException(
                                "Request not found: '" + name + "' in collection: " + colName));

                System.out.println(MAPPER.writeValueAsString(saved));
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Command(name = "remove", description = "Remove a saved request")
    static class RemoveCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection name (default: from config)")
        String collection;

        @Override
        public Integer call() {
            try {
                String colName = req.resolveCollection(collection);
                Collection col = req.storage().loadCollection(colName);

                boolean removed = col.getRequests().removeIf(r -> r.getName().equals(name));
                if (!removed) {
                    System.err.println("Error: request not found: '" + name + "' in collection: " + colName);
                    return 1;
                }
                req.storage().saveCollection(col);
                System.out.println("Removed request '" + name + "' from collection: " + colName);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
