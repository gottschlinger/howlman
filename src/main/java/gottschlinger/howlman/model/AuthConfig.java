package gottschlinger.howlman.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthConfig {

    private AuthType type = AuthType.NONE;
    private String token;
    private String username;
    private String password;

    public AuthConfig() {}

    public AuthType getType() { return type; }
    public void setType(AuthType type) { this.type = type; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
