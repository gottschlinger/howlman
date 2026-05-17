package gottschlinger.howlman.service;

import gottschlinger.howlman.model.AuthConfig;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.SavedRequest;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class CurlGenerator {

    public String generate(SavedRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add("curl -X " + request.getMethod().name());

        // headers
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                parts.add("-H '" + entry.getKey() + ": " + entry.getValue() + "'");
            }
        }

        // implicit Content-Type if not already present
        if (!hasContentTypeHeader(request)) {
            String implicit = implicitContentType(request);
            if (implicit != null) {
                parts.add("-H 'Content-Type: " + implicit + "'");
            }
        }

        // auth
        String authHeader = buildAuthHeader(request.getAuth());
        if (authHeader != null) {
            parts.add("-H 'Authorization: " + authHeader + "'");
        }

        // body
        if (request.getBody() != null && request.getBodyType() != BodyType.NONE) {
            parts.add("-d '" + request.getBody().replace("'", "'\\''") + "'");
        }

        // URL
        parts.add("'" + request.getUrl() + "'");

        return String.join(" \\\n  ", parts);
    }

    private boolean hasContentTypeHeader(SavedRequest request) {
        return request.getHeaders() != null
                && request.getHeaders().keySet().stream()
                        .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
    }

    private String implicitContentType(SavedRequest request) {
        if (request.getBodyType() == BodyType.JSON) return "application/json";
        if (request.getBodyType() == BodyType.FORM) return "application/x-www-form-urlencoded";
        return null;
    }

    private String buildAuthHeader(AuthConfig auth) {
        if (auth == null || auth.getType() == AuthType.NONE) return null;
        if (auth.getType() == AuthType.BEARER) return "Bearer " + auth.getToken();
        if (auth.getType() == AuthType.BASIC) {
            String credentials = auth.getUsername() + ":" + auth.getPassword();
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        }
        return null;
    }
}
