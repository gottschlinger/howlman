package gottschlinger.howlman.cli;

import gottschlinger.howlman.HowlMan;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.service.*;
import gottschlinger.howlman.model.*;
import gottschlinger.howlman.service.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Execute an ad-hoc HTTP request"
)
public class RunCommand implements Callable<Integer> {

    @ParentCommand HowlMan parent;

    @Option(names = {"--method", "-X"}, description = "HTTP method (default: GET)")
    HttpMethod method = HttpMethod.GET;

    @Option(names = "--url", required = true, description = "Request URL (supports {{variables}})")
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

    @Option(names = "--env", description = "Environment name (overrides active environment)")
    String env;

    @Option(names = "--curl", description = "Print curl command instead of executing")
    boolean curlMode;

    @Option(names = "--verbose", description = "Print response headers")
    boolean verbose;

    @Option(names = "--extract", description = "Extract varName=json.path from response and save to environment (repeatable)")
    List<String> extract;

    @Option(names = "--extract-env", description = "Environment to save extracted variables into (default: active environment)")
    String extractEnv;

    @Override
    public Integer call() {
        SavedRequest resolved;
        try {
            String envName = resolveEnvName();
            Map<String, String> variables = parent.storage.resolveVariables(envName);
            SavedRequest request = buildRequest();
            resolved = new InterpolationService().interpolate(request, variables);
            InterpolationService.warnUnresolved(resolved);
        } catch (MalformedStorageException e) {
            System.err.println("Error: " + e.getMessage());
            return 3;
        } catch (Exception e) {
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
            if (extract != null && !extract.isEmpty() && response.statusCode() < 400) {
                applyExtractions(response);
            }
            return response.statusCode() < 400 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("Error: connection failed: " + e.getMessage());
            return 2;
        }
    }

    private void applyExtractions(java.net.http.HttpResponse<String> response) {
        Map<String, String> extracted = new gottschlinger.howlman.service.ResponseExtractor().extract(response, extract);
        if (extracted.isEmpty()) return;
        try {
            String targetEnv = extractEnv != null ? extractEnv : parent.storage.loadConfig().getActiveEnvironment();
            if (targetEnv == null || targetEnv.isBlank()) {
                System.err.println("Warning: no target environment for extraction; use --extract-env or set an active environment.");
                return;
            }
            gottschlinger.howlman.model.Environment env = parent.storage.loadEnvironment(targetEnv);
            extracted.forEach((k, v) -> {
                env.getVariables().put(k, v);
                System.err.println("Saved " + k + " → " + targetEnv);
            });
            parent.storage.saveEnvironment(env);
        } catch (Exception e) {
            System.err.println("Warning: could not save extracted variables: " + e.getMessage());
        }
    }

    SavedRequest buildRequest() {
        SavedRequest req = new SavedRequest();
        req.setMethod(method);
        req.setUrl(url);
        req.setBody(body);
        req.setBodyType(bodyType);

        if (headers != null && !headers.isEmpty()) {
            Map<String, String> headerMap = new LinkedHashMap<>();
            for (String header : headers) {
                int colon = header.indexOf(':');
                if (colon < 1) {
                    System.err.println("Warning: skipping malformed header (expected Key:Value): " + header);
                    continue;
                }
                headerMap.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
            }
            req.setHeaders(headerMap);
        }

        if (authType != AuthType.NONE) {
            AuthConfig auth = new AuthConfig();
            auth.setType(authType);
            auth.setToken(token);
            auth.setUsername(username);
            auth.setPassword(password);
            req.setAuth(auth);
        }

        return req;
    }

    private String resolveEnvName() throws Exception {
        if (env != null) return env;
        return parent.storage.loadConfig().getActiveEnvironment();
    }
}
