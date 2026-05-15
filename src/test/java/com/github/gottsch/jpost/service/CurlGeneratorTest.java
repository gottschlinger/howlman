package com.github.gottsch.jpost.service;

import com.github.gottsch.jpost.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurlGeneratorTest {

    CurlGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CurlGenerator();
    }

    private static SavedRequest get(String url) {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.GET);
        req.setUrl(url);
        return req;
    }

    // ── basic structure ───────────────────────────────────────────────────────

    @Test
    void generate_get_producesMinimalCurl() {
        String curl = generator.generate(get("https://api.example.com/users"));
        assertEquals("curl -X GET \\\n  'https://api.example.com/users'", curl);
    }

    @Test
    void generate_post_includesMethod() {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com/users");
        req.setBody("{\"name\":\"alice\"}");
        req.setBodyType(BodyType.JSON);

        String curl = generator.generate(req);
        assertTrue(curl.startsWith("curl -X POST"));
        assertTrue(curl.contains("-d '{\"name\":\"alice\"}'"));
        assertTrue(curl.endsWith("'https://api.example.com/users'"));
    }

    // ── Content-Type injection ────────────────────────────────────────────────

    @Test
    void generate_jsonBody_addsContentTypeHeader() {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com");
        req.setBody("{}");
        req.setBodyType(BodyType.JSON);

        String curl = generator.generate(req);
        assertTrue(curl.contains("-H 'Content-Type: application/json'"));
    }

    @Test
    void generate_formBody_addsFormContentTypeHeader() {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com");
        req.setBody("user=alice&pass=secret");
        req.setBodyType(BodyType.FORM);

        String curl = generator.generate(req);
        assertTrue(curl.contains("-H 'Content-Type: application/x-www-form-urlencoded'"));
    }

    @Test
    void generate_explicitContentType_notDuplicated() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain");

        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com");
        req.setBody("hello");
        req.setBodyType(BodyType.JSON);
        req.setHeaders(headers);

        String curl = generator.generate(req);
        assertEquals(1, countOccurrences(curl, "Content-Type"));
        assertTrue(curl.contains("Content-Type: text/plain"));
    }

    // ── custom headers ────────────────────────────────────────────────────────

    @Test
    void generate_customHeaders_allIncluded() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("X-Tenant", "acme");

        SavedRequest req = get("https://api.example.com");
        req.setHeaders(headers);

        String curl = generator.generate(req);
        assertTrue(curl.contains("-H 'Accept: application/json'"));
        assertTrue(curl.contains("-H 'X-Tenant: acme'"));
    }

    // ── auth ─────────────────────────────────────────────────────────────────

    @Test
    void generate_bearerAuth_addsAuthorizationHeader() {
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("abc123");

        SavedRequest req = get("https://api.example.com");
        req.setAuth(auth);

        String curl = generator.generate(req);
        assertTrue(curl.contains("-H 'Authorization: Bearer abc123'"));
    }

    @Test
    void generate_basicAuth_addsBase64AuthorizationHeader() {
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BASIC);
        auth.setUsername("alice");
        auth.setPassword("secret");

        SavedRequest req = get("https://api.example.com");
        req.setAuth(auth);

        String expected = "Basic " + Base64.getEncoder().encodeToString("alice:secret".getBytes());
        String curl = generator.generate(req);
        assertTrue(curl.contains("-H 'Authorization: " + expected + "'"));
    }

    @Test
    void generate_noAuth_noAuthorizationHeader() {
        String curl = generator.generate(get("https://api.example.com"));
        assertFalse(curl.contains("Authorization"));
    }

    // ── body with single quotes ───────────────────────────────────────────────

    @Test
    void generate_bodyWithSingleQuote_escaped() {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com");
        req.setBody("it's here");
        req.setBodyType(BodyType.RAW);

        String curl = generator.generate(req);
        // single quotes in body are shell-escaped as '\''
        assertTrue(curl.contains("it'\\''s here"));
    }

    // ── full example ──────────────────────────────────────────────────────────

    @Test
    void generate_fullRequest_matchesExpectedFormat() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");

        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("tok");

        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("https://api.example.com/users");
        req.setHeaders(headers);
        req.setBody("{\"name\":\"bob\"}");
        req.setBodyType(BodyType.JSON);
        req.setAuth(auth);

        String curl = generator.generate(req);
        String expected = "curl -X POST \\\n" +
                "  -H 'Accept: application/json' \\\n" +
                "  -H 'Content-Type: application/json' \\\n" +
                "  -H 'Authorization: Bearer tok' \\\n" +
                "  -d '{\"name\":\"bob\"}' \\\n" +
                "  'https://api.example.com/users'";
        assertEquals(expected, curl);
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) { count++; idx++; }
        return count;
    }
}
