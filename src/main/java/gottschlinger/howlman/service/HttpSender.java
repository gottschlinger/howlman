package gottschlinger.howlman.service;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@FunctionalInterface
public interface HttpSender {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
