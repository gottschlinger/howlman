package com.github.gottsch.jpost.service;

import com.github.gottsch.jpost.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpServiceTest {

    @Mock HttpSender mockSender;
    @Mock HttpResponse<String> mockResponse;

    HttpService service;

    @BeforeEach
    void setUp() {
        service = new HttpService(mockSender);
    }

    @SuppressWarnings("unchecked")
    private void stubSender() throws Exception {
        when(mockSender.send(any())).thenReturn(mockResponse);
    }

    private HttpRequest captureRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockSender).send(captor.capture());
        return captor.getValue();
    }

    private static SavedRequest get(String url) {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.GET);
        req.setUrl(url);
        return req;
    }

    // ── method + URL ──────────────────────────────────────────────────────────

    @Test
    void execute_setsMethodAndUrl() throws Exception {
        stubSender();
        service.execute(get("http://example.com/users"));

        HttpRequest sent = captureRequest();
        assertEquals("GET", sent.method());
        assertEquals("http://example.com/users", sent.uri().toString());
    }

    @Test
    void execute_postWithJsonBody_setsContentType() throws Exception {
        stubSender();
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("http://example.com/users");
        req.setBody("{\"name\":\"alice\"}");
        req.setBodyType(BodyType.JSON);

        service.execute(req);

        HttpRequest sent = captureRequest();
        assertEquals("POST", sent.method());
        assertTrue(sent.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void execute_formBody_setsFormContentType() throws Exception {
        stubSender();
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("http://example.com/login");
        req.setBody("user=alice&pass=secret");
        req.setBodyType(BodyType.FORM);

        service.execute(req);

        HttpRequest sent = captureRequest();
        assertTrue(sent.headers().firstValue("Content-Type").orElse("").contains("application/x-www-form-urlencoded"));
    }

    // ── headers ───────────────────────────────────────────────────────────────

    @Test
    void execute_customHeadersAreSent() throws Exception {
        stubSender();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("X-Tenant", "acme");

        SavedRequest req = get("http://example.com");
        req.setHeaders(headers);
        service.execute(req);

        HttpRequest sent = captureRequest();
        assertEquals("application/json", sent.headers().firstValue("Accept").orElse(""));
        assertEquals("acme", sent.headers().firstValue("X-Tenant").orElse(""));
    }

    @Test
    void execute_explicitContentType_notOverridden() throws Exception {
        stubSender();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain");

        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.POST);
        req.setUrl("http://example.com");
        req.setBody("hello");
        req.setBodyType(BodyType.JSON);
        req.setHeaders(headers);
        service.execute(req);

        HttpRequest sent = captureRequest();
        assertEquals(1, sent.headers().allValues("Content-Type").size());
        assertEquals("text/plain", sent.headers().firstValue("Content-Type").orElse(""));
    }

    // ── auth ─────────────────────────────────────────────────────────────────

    @Test
    void execute_bearerAuth_setsAuthorizationHeader() throws Exception {
        stubSender();
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BEARER);
        auth.setToken("mytoken");

        SavedRequest req = get("http://example.com");
        req.setAuth(auth);
        service.execute(req);

        HttpRequest sent = captureRequest();
        assertEquals("Bearer mytoken", sent.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void execute_basicAuth_setsBase64AuthorizationHeader() throws Exception {
        stubSender();
        AuthConfig auth = new AuthConfig();
        auth.setType(AuthType.BASIC);
        auth.setUsername("alice");
        auth.setPassword("secret");

        SavedRequest req = get("http://example.com");
        req.setAuth(auth);
        service.execute(req);

        String expected = "Basic " + Base64.getEncoder().encodeToString("alice:secret".getBytes());
        HttpRequest sent = captureRequest();
        assertEquals(expected, sent.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void execute_noAuth_noAuthorizationHeader() throws Exception {
        stubSender();
        service.execute(get("http://example.com"));

        HttpRequest sent = captureRequest();
        assertTrue(sent.headers().firstValue("Authorization").isEmpty());
    }

    // ── response passthrough ──────────────────────────────────────────────────

    @Test
    void execute_returnsResponseFromSender() throws Exception {
        when(mockSender.send(any())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn("{\"created\":true}");

        HttpResponse<String> result = service.execute(get("http://example.com"));

        assertEquals(201, result.statusCode());
        assertEquals("{\"created\":true}", result.body());
    }
}
