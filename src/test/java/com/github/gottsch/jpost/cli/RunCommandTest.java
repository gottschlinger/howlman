package com.github.gottsch.jpost.cli;

import com.github.gottsch.jpost.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunCommandTest {

    RunCommand cmd;

    @BeforeEach
    void setUp() {
        cmd = new RunCommand();
        cmd.method = HttpMethod.GET;
        cmd.url = "http://example.com";
        cmd.bodyType = BodyType.NONE;
        cmd.authType = AuthType.NONE;
    }

    // ── buildRequest basics ───────────────────────────────────────────────────

    @Test
    void buildRequest_defaultsToGet() {
        SavedRequest req = cmd.buildRequest();
        assertEquals(HttpMethod.GET, req.getMethod());
    }

    @Test
    void buildRequest_setsUrlAndMethod() {
        cmd.method = HttpMethod.POST;
        cmd.url = "http://api.example.com/users";
        SavedRequest req = cmd.buildRequest();
        assertEquals(HttpMethod.POST, req.getMethod());
        assertEquals("http://api.example.com/users", req.getUrl());
    }

    @Test
    void buildRequest_setsBodyAndBodyType() {
        cmd.body = "{\"name\":\"alice\"}";
        cmd.bodyType = BodyType.JSON;
        SavedRequest req = cmd.buildRequest();
        assertEquals("{\"name\":\"alice\"}", req.getBody());
        assertEquals(BodyType.JSON, req.getBodyType());
    }

    // ── header parsing ────────────────────────────────────────────────────────

    @Test
    void buildRequest_parsesHeaderKeyValue() {
        cmd.headers = List.of("Accept: application/json", "X-Tenant: acme");
        SavedRequest req = cmd.buildRequest();
        assertEquals("application/json", req.getHeaders().get("Accept"));
        assertEquals("acme", req.getHeaders().get("X-Tenant"));
    }

    @Test
    void buildRequest_headerWithColonInValue() {
        cmd.headers = List.of("Authorization: Bearer tok:en");
        SavedRequest req = cmd.buildRequest();
        // only the first colon is the separator
        assertEquals("Bearer tok:en", req.getHeaders().get("Authorization"));
    }

    @Test
    void buildRequest_noHeaders_headersIsNull() {
        SavedRequest req = cmd.buildRequest();
        assertNull(req.getHeaders());
    }

    // ── auth ─────────────────────────────────────────────────────────────────

    @Test
    void buildRequest_bearerAuth_setsAuthConfig() {
        cmd.authType = AuthType.BEARER;
        cmd.token = "mytoken";
        SavedRequest req = cmd.buildRequest();
        assertNotNull(req.getAuth());
        assertEquals(AuthType.BEARER, req.getAuth().getType());
        assertEquals("mytoken", req.getAuth().getToken());
    }

    @Test
    void buildRequest_basicAuth_setsAuthConfig() {
        cmd.authType = AuthType.BASIC;
        cmd.username = "alice";
        cmd.password = "secret";
        SavedRequest req = cmd.buildRequest();
        assertNotNull(req.getAuth());
        assertEquals(AuthType.BASIC, req.getAuth().getType());
        assertEquals("alice", req.getAuth().getUsername());
        assertEquals("secret", req.getAuth().getPassword());
    }

    @Test
    void buildRequest_noAuth_authIsNull() {
        SavedRequest req = cmd.buildRequest();
        assertNull(req.getAuth());
    }
}
