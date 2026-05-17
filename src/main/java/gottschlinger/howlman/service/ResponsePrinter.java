package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.net.http.HttpResponse;
import java.util.Map;

public class ResponsePrinter {

    private static final String SEPARATOR = "-----------------------------";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void print(HttpResponse<String> response, boolean verbose) {
        int status = response.statusCode();
        boolean isError = status >= 400;
        java.io.PrintStream out = isError ? System.err : System.out;

        out.println("HTTP " + status + " " + reasonPhrase(status));
        out.println(SEPARATOR);

        if (verbose) {
            response.headers().map().forEach((name, values) ->
                    values.forEach(v -> out.println(name + ": " + v)));
            out.println(SEPARATOR);
        }

        String body = response.body();
        if (body != null && !body.isBlank()) {
            out.println();
            out.println(prettyIfJson(response, body));
        }
    }

    private String prettyIfJson(HttpResponse<String> response, String body) {
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (!contentType.contains("application/json")) {
            return body;
        }
        try {
            Object parsed = MAPPER.readValue(body, Object.class);
            return MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            return body;
        }
    }

    private String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default  -> "";
        };
    }
}
