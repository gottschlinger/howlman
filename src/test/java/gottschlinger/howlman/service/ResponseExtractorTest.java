package gottschlinger.howlman.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

class ResponseExtractorTest {

    private final ResponseExtractor extractor = new ResponseExtractor();

    @Test
    void simpleTopLevelField() {
        var response = stubResponse(200, "{\"access_token\":\"abc123\"}");
        Map<String, String> result = extractor.extract(response, List.of("token=access_token"));
        assertEquals(Map.of("token", "abc123"), result);
    }

    @Test
    void nestedField() {
        var response = stubResponse(200, "{\"data\":{\"user\":{\"id\":\"u42\"}}}");
        Map<String, String> result = extractor.extract(response, List.of("userId=data.user.id"));
        assertEquals(Map.of("userId", "u42"), result);
    }

    @Test
    void multipleSpecs() {
        var response = stubResponse(200, "{\"access_token\":\"tok\",\"expires_in\":3600}");
        Map<String, String> result = extractor.extract(response,
                List.of("token=access_token", "ttl=expires_in"));
        assertEquals("tok", result.get("token"));
        assertEquals("3600", result.get("ttl"));
    }

    @Test
    void missingFieldReturnsEmptyEntryAndWarns() {
        var response = stubResponse(200, "{\"foo\":\"bar\"}");
        Map<String, String> result = extractor.extract(response, List.of("token=access_token"));
        assertTrue(result.isEmpty());
    }

    @Test
    void nonJsonBodyReturnsEmpty() {
        var response = stubResponse(200, "plain text, not json");
        Map<String, String> result = extractor.extract(response, List.of("token=access_token"));
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyBodyReturnsEmpty() {
        var response = stubResponse(200, "");
        Map<String, String> result = extractor.extract(response, List.of("token=access_token"));
        assertTrue(result.isEmpty());
    }

    @Test
    void malformedSpecIsSkipped() {
        var response = stubResponse(200, "{\"access_token\":\"abc\"}");
        Map<String, String> result = extractor.extract(response, List.of("badspec"));
        assertTrue(result.isEmpty());
    }

    @Test
    void nullSpecsReturnsEmpty() {
        var response = stubResponse(200, "{\"access_token\":\"abc\"}");
        Map<String, String> result = extractor.extract(response, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void objectValueSerializedAsJson() {
        var response = stubResponse(200, "{\"meta\":{\"page\":1}}");
        Map<String, String> result = extractor.extract(response, List.of("meta=meta"));
        assertTrue(result.containsKey("meta"));
        assertTrue(result.get("meta").contains("page"));
    }

    // ── stub helper ──────────────────────────────────────────────────────────

    private HttpResponse<String> stubResponse(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return URI.create("http://example.com"); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
