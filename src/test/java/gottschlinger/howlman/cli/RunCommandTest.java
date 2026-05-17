package gottschlinger.howlman.cli;

import gottschlinger.howlman.model.*;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.HttpMethod;
import gottschlinger.howlman.model.SavedRequest;
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

    // â”€â”€ buildRequest basics â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ header parsing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ auth â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
