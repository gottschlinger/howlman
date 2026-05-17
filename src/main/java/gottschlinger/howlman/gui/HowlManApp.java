package gottschlinger.howlman.gui;

import gottschlinger.howlman.gui.controller.MainController;
import gottschlinger.howlman.service.StorageService;
import gottschlinger.howlman.util.ConfigPaths;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

public class HowlManApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(HowlManApp.class.getResource("main.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();
        StorageService storage = new StorageService(new ConfigPaths());
        storage.init();
        controller.init(storage);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width  = Math.min(Math.max(screen.getWidth()  * 0.82, 900), 1600);
        double height = Math.min(Math.max(screen.getHeight() * 0.85, 640), 1080);

        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(HowlManApp.class.getResource("style.css").toExternalForm());

        primaryStage.getIcons().add(loadIcon());
        primaryStage.setTitle("HowlMan");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    /**
     * Tries to load icon.png from the gui resources package.
     * Falls back to a programmatically drawn placeholder if the file is absent.
     * To use your own icon: place icon.png next to main.fxml in
     *   src/main/resources/com/github/gottsch/jpost/gui/
     */
    private Image loadIcon() {
        InputStream is = HowlManApp.class.getResourceAsStream("icon.png");
        if (is != null) {
            return new Image(is);
        }
        return buildPlaceholderIcon();
    }

    private Image buildPlaceholderIcon() {
        Canvas canvas = new Canvas(64, 64);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Outer dark background
        gc.setFill(Color.web("#1e2a3a"));
        gc.fillRoundRect(0, 0, 64, 64, 14, 14);

        // Inner accent square
        gc.setFill(Color.web("#0066cc"));
        gc.fillRoundRect(7, 7, 50, 50, 10, 10);

        // "JP" text
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("HM", 32, 39);

        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
