package gottschlinger.howlman.service;

import gottschlinger.howlman.model.AuthConfig;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.Map;

public class HttpService {

    private final HttpSender sender;

    public HttpService() {
        // Pin to HTTP/1.1 so a literal Host header is sent (HTTP/2 replaces it
        // with the :authority pseudo-header, which some servers/WAFs reject).
        // Matches Postman's default behavior.
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.sender = request -> client.send(request, BodyHandlers.ofString());
    }

    public HttpService(HttpSender sender) {
        this.sender = sender;
    }

    public HttpResponse<String> execute(SavedRequest request) throws IOException, InterruptedException {
        String rawUrl = request.getUrl();
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL — check for unresolved {{variables}}: " + rawUrl, e);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);

        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        injectAuth(builder, request.getAuth());
        injectContentTypeIfAbsent(builder, request);
        injectDefaultHeadersIfAbsent(builder, request);

        BodyPublisher publisher = buildPublisher(request);
        builder.method(request.getMethod().name(), publisher);

        return sender.send(builder.build());
    }

    private void injectAuth(HttpRequest.Builder builder, AuthConfig auth) {
        if (auth == null || auth.getType() == AuthType.NONE) {
            return;
        }
        if (auth.getType() == AuthType.BEARER) {
            builder.header("Authorization", "Bearer " + auth.getToken());
        } else if (auth.getType() == AuthType.BASIC) {
            String credentials = auth.getUsername() + ":" + auth.getPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    private BodyPublisher buildPublisher(SavedRequest request) {
        BodyType bodyType = request.getBodyType();
        String body = request.getBody();
        if (bodyType == null || bodyType == BodyType.NONE || body == null) {
            return BodyPublishers.noBody();
        }
        return BodyPublishers.ofString(body);
    }

    /**
     * Adds headers Postman/browsers send by default but the JDK omits, unless the
     * user already set them. Accept-Encoding is intentionally NOT advertised: the
     * JDK HttpClient does not auto-decompress, so a gzipped response would arrive
     * as undecodable bytes.
     */
    private void injectDefaultHeadersIfAbsent(HttpRequest.Builder builder, SavedRequest request) {
        if (!hasHeader(request, "User-Agent")) {
            builder.header("User-Agent", "HowlMan/1.1.0");
        }
        if (!hasHeader(request, "Accept")) {
            builder.header("Accept", "*/*");
        }
    }

    private boolean hasHeader(SavedRequest request, String name) {
        return request.getHeaders() != null
                && request.getHeaders().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
    }

    private void injectContentTypeIfAbsent(HttpRequest.Builder builder, SavedRequest request) {
        boolean hasContentType = request.getHeaders() != null
                && request.getHeaders().keySet().stream()
                        .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        if (hasContentType) {
            return;
        }
        BodyType bodyType = request.getBodyType();
        if (bodyType == BodyType.JSON) {
            builder.header("Content-Type", "application/json");
        } else if (bodyType == BodyType.FORM) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
        }
    }
}
