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
        Application.launch(HowlManApp.class, args);
    }
}
