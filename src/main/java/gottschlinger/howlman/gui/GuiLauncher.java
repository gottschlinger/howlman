package gottschlinger.howlman.gui;

import javafx.application.Application;

/**
 * Separate launcher class required when JavaFX is on the module path.
 * The main class for jpackage must NOT extend Application directly.
 */
public class GuiLauncher {

    public static void main(String[] args) {
        Application.launch(HowlManApp.class, args);
    }
}
