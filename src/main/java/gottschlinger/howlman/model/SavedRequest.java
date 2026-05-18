package gottschlinger.howlman.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SavedRequest {

    private String name;
    private HttpMethod method = HttpMethod.GET;
    private String url;
    private Map<String, String> headers;
    private String body;
    private BodyType bodyType = BodyType.NONE;
    private AuthConfig auth;
    private Map<String, String> preVars;
    private boolean preEnabled;
    private Map<String, String> extracts;
    private boolean extractEnabled;

    public SavedRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public HttpMethod getMethod() { return method; }
    public void setMethod(HttpMethod method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public BodyType getBodyType() { return bodyType; }
    public void setBodyType(BodyType bodyType) { this.bodyType = bodyType; }

    public AuthConfig getAuth() { return auth; }
    public void setAuth(AuthConfig auth) { this.auth = auth; }

    public Map<String, String> getPreVars() { return preVars; }
    public void setPreVars(Map<String, String> preVars) { this.preVars = preVars; }

    public boolean isPreEnabled() { return preEnabled; }
    public void setPreEnabled(boolean preEnabled) { this.preEnabled = preEnabled; }

    public Map<String, String> getExtracts() { return extracts; }
    public void setExtracts(Map<String, String> extracts) { this.extracts = extracts; }

    public boolean isExtractEnabled() { return extractEnabled; }
    public void setExtractEnabled(boolean extractEnabled) { this.extractEnabled = extractEnabled; }
}
