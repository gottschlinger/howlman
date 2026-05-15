package com.github.gottsch.jpost.service;

import com.github.gottsch.jpost.model.AuthConfig;
import com.github.gottsch.jpost.model.AuthType;
import com.github.gottsch.jpost.model.BodyType;
import com.github.gottsch.jpost.model.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.Map;

public class HttpService {

    private final HttpSender sender;

    public HttpService() {
        HttpClient client = HttpClient.newHttpClient();
        this.sender = request -> client.send(request, BodyHandlers.ofString());
    }

    public HttpService(HttpSender sender) {
        this.sender = sender;
    }

    public HttpResponse<String> execute(SavedRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.getUrl()));

        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        injectAuth(builder, request.getAuth());
        injectContentTypeIfAbsent(builder, request);

        BodyPublisher publisher = buildPublisher(request);
        builder.method(request.getMethod().name(), publisher);

        return sender.send(builder.build());
    }

    private void injectAuth(HttpRequest.Builder builder, AuthConfig auth) {
        if (auth == null || auth.getType() == AuthType.NONE) {
            return;
        }
        if (auth.getType() == AuthType.BEARER) {
            builder.header("Authorization", "Bearer " + auth.getToken());
        } else if (auth.getType() == AuthType.BASIC) {
            String credentials = auth.getUsername() + ":" + auth.getPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    private BodyPublisher buildPublisher(SavedRequest request) {
        BodyType bodyType = request.getBodyType();
        String body = request.getBody();
        if (bodyType == null || bodyType == BodyType.NONE || body == null) {
            return BodyPublishers.noBody();
        }
        return BodyPublishers.ofString(body);
    }

    private void injectContentTypeIfAbsent(HttpRequest.Builder builder, SavedRequest request) {
        boolean hasContentType = request.getHeaders() != null
                && request.getHeaders().keySet().stream()
                        .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        if (hasContentType) {
            return;
        }
        BodyType bodyType = request.getBodyType();
        if (bodyType == BodyType.JSON) {
            builder.header("Content-Type", "application/json");
        } else if (bodyType == BodyType.FORM) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
        }
    }
}
