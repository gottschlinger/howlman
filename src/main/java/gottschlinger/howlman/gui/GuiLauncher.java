package gottschlinger.howlman.gui;

import javafx.application.Application;

/**
 * Separate launcher class required when JavaFX is on the module path.
 * The main class for jpackage must NOT extend Application directly.
 */
public class GuiLauncher {

    public static void main(String[] args) {
        // Must be set before any HttpClient activity — the JDK caches the
        // restricted-header allowlist once. Unlocks normally-managed headers
        // (Host, Connection, etc.) so users can override them.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host,connection,upgrade,expect");
        // Log the literal outgoing request (request line + every header, including
        // JDK-injected Host/User-Agent/Content-Length) to stderr — for debugging 400s.
        System.setProperty("jdk.httpclient.HttpClient.log", "requests,headers");
        Application.launch(HowlManApp.class, args);
    }
}
