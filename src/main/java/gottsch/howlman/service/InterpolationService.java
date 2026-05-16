package gottsch.howlman.service;

import gottsch.howlman.model.AuthConfig;
import gottsch.howlman.model.AuthType;
import gottsch.howlman.model.SavedRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InterpolationService {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public String interpolate(String input, Map<String, String> variables) {
        if (input == null || variables == null || variables.isEmpty()) {
            return input;
        }
        Matcher m = TOKEN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = variables.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void warnUnresolved(SavedRequest resolved) {
        List<String> warnings = new ArrayList<>();
        collectUnresolved(resolved.getUrl(), "url", warnings);
        collectUnresolved(resolved.getBody(), "body", warnings);
        if (resolved.getHeaders() != null) {
            resolved.getHeaders().forEach((k, v) -> collectUnresolved(v, "header:" + k, warnings));
        }
        if (resolved.getAuth() != null) {
            collectUnresolved(resolved.getAuth().getToken(), "auth.token", warnings);
            collectUnresolved(resolved.getAuth().getUsername(), "auth.username", warnings);
            collectUnresolved(resolved.getAuth().getPassword(), "auth.password", warnings);
        }
        warnings.forEach(w -> System.err.println("Warning: " + w));
    }

    private static void collectUnresolved(String value, String field, List<String> out) {
        if (value == null) return;
        Matcher m = TOKEN.matcher(value);
        while (m.find()) {
            out.add("unresolved variable {{" + m.group(1) + "}} in " + field);
        }
    }

    public SavedRequest interpolate(SavedRequest request, Map<String, String> variables) {
        SavedRequest out = new SavedRequest();
        out.setName(request.getName());
        out.setMethod(request.getMethod());
        out.setBodyType(request.getBodyType());
        out.setUrl(interpolate(request.getUrl(), variables));
        out.setBody(interpolate(request.getBody(), variables));

        if (request.getHeaders() != null) {
            Map<String, String> headers = new LinkedHashMap<>();
            request.getHeaders().forEach((k, v) -> headers.put(k, interpolate(v, variables)));
            out.setHeaders(headers);
        }

        if (request.getAuth() != null) {
            AuthConfig src = request.getAuth();
            AuthConfig auth = new AuthConfig();
            auth.setType(src.getType());
            if (src.getType() == AuthType.BEARER) {
                auth.setToken(interpolate(src.getToken(), variables));
            } else if (src.getType() == AuthType.BASIC) {
                auth.setUsername(interpolate(src.getUsername(), variables));
                auth.setPassword(interpolate(src.getPassword(), variables));
            }
            out.setAuth(auth);
        }

        return out;
    }
}
