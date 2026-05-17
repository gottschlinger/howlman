package gottschlinger.howlman.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gottschlinger.howlman.HowlMan;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.service.*;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.service.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Parses a collection path string of the form {@code collection[/folder/subfolder]}.
     * The first segment is the collection name; remaining segments are the folder path.
     */
    record CollectionPath(String collection, List<String> folderPath) {
        static CollectionPath parse(String raw) {
            String[] parts = raw.split("/");
            String col = parts[0];
            List<String> folder = parts.length > 1
                    ? List.of(Arrays.copyOfRange(parts, 1, parts.length))
                    : List.of();
            return new CollectionPath(col, folder);
        }
    }

    private CollectionPath resolveCollectionPath(String option) throws IOException {
        String raw = option != null ? option : storage().loadConfig().getDefaultCollection();
        if (raw == null || raw.isBlank()) raw = "default";
        return CollectionPath.parse(raw);
    }

    /** A located request ready to act on: collection object, name, folder path, and the request itself. */
    record ResolvedRequest(RequestCollection col, String collectionName,
                           List<String> folderPath, SavedRequest request) {}

    /**
     * Finds a request by name within the given collection option.
     * When no folder path is specified, falls back to a full-collection search if not found at the top level.
     * Errors on ambiguous matches (same name in multiple folders).
     */
    private ResolvedRequest resolveRequest(String collectionOption, String name) throws IOException {
        CollectionPath cp = resolveCollectionPath(collectionOption);
        RequestCollection col = storage().loadCollection(cp.collection());

        Optional<SavedRequest> direct = StorageService.findRequest(col, cp.folderPath(), name);
        if (direct.isPresent()) {
            return new ResolvedRequest(col, cp.collection(), cp.folderPath(), direct.get());
        }

        // Only do fuzzy search when no folder path was explicitly given
        if (!cp.folderPath().isEmpty()) {
            throw new IOException("Request not found: '" + name + "' in: "
                    + cp.collection() + "/" + String.join("/", cp.folderPath()));
        }

        List<StorageService.LocatedRequest> matches = StorageService.findRequestAnywhere(col, name);
        if (matches.isEmpty()) {
            throw new IOException("Request not found: '" + name + "' in collection: " + cp.collection());
        }
        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder("Ambiguous: '" + name + "' found in multiple locations:\n");
            for (var m : matches) {
                sb.append("  ").append(cp.collection());
                if (!m.folderPath().isEmpty()) sb.append("/").append(String.join("/", m.folderPath()));
                sb.append("\n");
            }
            throw new IOException(sb.toString().trim());
        }

        StorageService.LocatedRequest match = matches.get(0);
        return new ResolvedRequest(col, cp.collection(), match.folderPath(), match.request());
    }

    // â”€â”€ save â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Command(name = "save", description = "Save a request to a collection (use collection/folder/subfolder for sub-folders)")
    static class SaveCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Option(names = "--name", required = true, description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection path, e.g. myapi or myapi/auth/v1 (default: from config)")
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
                CollectionPath cp = req.resolveCollectionPath(collection);
                RequestCollection col = loadOrCreateCollection(cp.collection());

                StorageService.ensureFolderPath(col, cp.folderPath());
                StorageService.upsertRequest(col, cp.folderPath(), buildRequest());
                req.storage().saveCollection(col);

                String location = cp.folderPath().isEmpty()
                        ? cp.collection()
                        : cp.collection() + "/" + String.join("/", cp.folderPath());
                System.out.println("Saved request '" + name + "' to: " + location);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private RequestCollection loadOrCreateCollection(String colName) throws IOException {
            try {
                return req.storage().loadCollection(colName);
            } catch (IOException e) {
                RequestCollection col = new RequestCollection(colName);
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

    // â”€â”€ run â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Command(name = "run", description = "Run a saved request")
    static class RunCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection path, e.g. myapi or myapi/auth/v1 (default: from config)")
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
                ResolvedRequest rr = req.resolveRequest(collection, name);
                String envName = env != null ? env : req.storage().loadConfig().getActiveEnvironment();
                Map<String, String> variables = req.storage().resolveVariables(envName);
                resolved = new InterpolationService().interpolate(rr.request(), variables);
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

    // â”€â”€ list â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Command(name = "list", description = "List saved requests")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Option(names = "--collection", description = "Collection path to list (default: all collections)")
        String collection;

        @Override
        public Integer call() {
            try {
                List<String> colNames = collection != null
                        ? List.of(CollectionPath.parse(collection).collection())
                        : req.storage().listCollectionNames();

                boolean[] any = {false};
                for (String colName : colNames) {
                    try {
                        RequestCollection col = req.storage().loadCollection(colName);
                        printRequests(col.getRequests(), col.getFolders(), colName, List.of(), any);
                    } catch (IOException e) {
                        System.err.println("Warning: could not load collection '" + colName + "': " + e.getMessage());
                    }
                }
                if (!any[0]) System.out.println("No saved requests found.");
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private static void printRequests(List<SavedRequest> requests, List<RequestFolder> folders,
                                          String colName, List<String> folderPath, boolean[] any) {
            String label = folderPath.isEmpty() ? colName : colName + "/" + String.join("/", folderPath);
            if (requests != null) {
                for (SavedRequest r : requests) {
                    System.out.printf("[%s] %-20s %-7s %s%n", label, r.getName(), r.getMethod(), r.getUrl());
                    any[0] = true;
                }
            }
            if (folders != null) {
                for (RequestFolder folder : folders) {
                    List<String> sub = new ArrayList<>(folderPath);
                    sub.add(folder.getName());
                    printRequests(folder.getRequests(), folder.getFolders(), colName, sub, any);
                }
            }
        }
    }

    // â”€â”€ show â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Command(name = "show", description = "Show a saved request as JSON")
    static class ShowCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection path, e.g. myapi or myapi/auth/v1 (default: from config)")
        String collection;

        private static final ObjectMapper MAPPER = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        @Override
        public Integer call() {
            try {
                ResolvedRequest rr = req.resolveRequest(collection, name);
                System.out.println(MAPPER.writeValueAsString(rr.request()));
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // â”€â”€ remove â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Command(name = "remove", description = "Remove a saved request")
    static class RemoveCommand implements Callable<Integer> {

        @ParentCommand RequestCommand req;

        @Parameters(index = "0", description = "Request name")
        String name;

        @Option(names = "--collection", description = "Collection path, e.g. myapi or myapi/auth/v1 (default: from config)")
        String collection;

        @Override
        public Integer call() {
            try {
                ResolvedRequest rr = req.resolveRequest(collection, name);
                StorageService.deleteRequest(rr.col(), rr.folderPath(), name);
                req.storage().saveCollection(rr.col());
                String location = rr.folderPath().isEmpty()
                        ? rr.collectionName()
                        : rr.collectionName() + "/" + String.join("/", rr.folderPath());
                System.out.println("Removed request '" + name + "' from: " + location);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
