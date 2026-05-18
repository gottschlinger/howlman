package gottschlinger.howlman.service;

import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterpolationServiceTest {

    InterpolationService service;

    @BeforeEach
    void setUp() {
        service = new InterpolationService();
    }

    // â”€â”€ String interpolation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void interpolate_replacesKnownToken() {
        String result = service.interpolate("{{baseUrl}}/api", Map.of("baseUrl", "http://localhost:8080"));
        assertEquals("http://localhost:8080/api", result);
    }

    @Test
    void interpolate_leavesUnknownTokenAsIs() {
        String result = service.interpolate("{{unknown}}/path", Map.of());
        assertEquals("{{unknown}}/path", result);
    }

    @Test
    void interpolate_replacesMultipleTokens() {
        String result = service.interpolate("{{scheme}}://{{host}}:{{port}}", Map.of(
                "scheme", "https",
                "host", "example.com",
                "port", "443"
        ));
        assertEquals("https://example.com:443", result);
    }

    @Test
    void interpolate_nullInput_returnsNull() {
        assertNull(service.interpolate((String) null, Map.of("key", "val")));
    }

    @Test
    void interpolate_nullVariables_returnsInput() {
        assertEquals("{{x}}", service.interpolate("{{x}}", null));
    }

    @Test
    void interpolate_noTokensInInput_returnsUnchanged() {
        assertEquals("plain string", service.interpolate("plain string", Map.of("key", "val")));
    }

    @Test
    void interpolate_partialMatch_leavesUnresolved() {
        String result = service.interpolate("{{known}}-{{unknown}}", Map.of("known", "A"));
        assertEquals("A-{{unknown}}", result);
    }

    @Test
    void interpolate_variableNameWithDash() {
        String result = service.interpolate("{{access-token}}", Map.of("access-token", "abc123"));
        assertEquals("abc123", result);
    }

    @Test
    void interpolate_variableNameWithMixedDashUnderscore() {
        String result = service.interpolate("{{my-var_1}}", Map.of("my-var_1", "value"));
        assertEquals("value", result);
    }

    // â”€â”€ SavedRequest interpolation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void interpolate_request_replacesUrlAndHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("X-Tenant", "{{tenant}}");

        SavedRequest req = new SavedRequest();
        req.setName("test");
        req.setMethod(HttpMethod.GET);
        req.setUrl("{{baseUrl}}/users");
        req.setHeaders(headers);

        SavedRequest out = service.interpolate(req, Map.of("baseUrl", "http://localhost", "tenant", "acme"));

        assertEquals("http://localhost/users", out.getUrl());
        assertEquals("application/json", out.getHeaders().get("Accept"));
        assertEquals("acme", out.getHeaders().get("X-Tenant"));
    }

    @Test
    void interpolate_request_replacesBody() {
        SavedRequest req = new SavedRequest();
        req.setName("test");
        req.setMethod(HttpMethod.POST);
        req.setUrl("http://example.com");
        req.setBody("{\"user\": \"{{username}}\"}");
        req.setBodyType(BodyType.JSON);

        SavedRequest out = service.interpolate(req, Map.of("username", "alice"));
        assertEquals("{\"user\": \"alice\"}", out.getBody());
    }

    @Test
    void interpolate_request_replacesBearerToken() {
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("{{authToken}}");

        SavedRequest req = new SavedRequest();
        req.setName("test");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://example.com");
        req.setAuth(auth);

        SavedRequest out = service.interpolate(req, Map.of("authToken", "secret123"));
        assertEquals("secret123", out.getAuth().getToken());
        assertNull(out.getAuth().getUsername());
    }

    @Test
    void interpolate_request_replacesBasicCredentials() {
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BASIC);
        auth.setUsername("{{user}}");
        auth.setPassword("{{pass}}");

        SavedRequest req = new SavedRequest();
        req.setName("test");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://example.com");
        req.setAuth(auth);

        SavedRequest out = service.interpolate(req, Map.of("user", "bob", "pass", "s3cr3t"));
        assertEquals("bob", out.getAuth().getUsername());
        assertEquals("s3cr3t", out.getAuth().getPassword());
        assertNull(out.getAuth().getToken());
    }

    @Test
    void interpolate_request_noAuth_copiedAsNull() {
        SavedRequest req = new SavedRequest();
        req.setName("test");
        req.setMethod(HttpMethod.GET);
        req.setUrl("http://example.com");

        SavedRequest out = service.interpolate(req, Map.of());
        assertNull(out.getAuth());
    }
}
