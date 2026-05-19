package gottschlinger.howlman.gui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.AppConfig;
import gottschlinger.howlman.model.AuthConfig;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.HttpMethod;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.SavedRequest;
import gottschlinger.howlman.service.HttpService;
import gottschlinger.howlman.service.InterpolationService;
import gottschlinger.howlman.service.StorageService;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class RequestTabController {

    // ── FXML fields ───────────────────────────────────────────────────────────
    @FXML private Label collectionBreadcrumb;
    @FXML private Label breadcrumbSep;
    @FXML private Label requestBreadcrumb;

    @FXML private ComboBox<String> methodCombo;
    @FXML private TextField urlField;
    @FXML private Button sendButton;

    @FXML private TableView<HeaderRow> headersTable;
    @FXML private TableColumn<HeaderRow, String> headerKeyCol;
    @FXML private TableColumn<HeaderRow, String> headerValueCol;

    @FXML private ComboBox<String> bodyTypeCombo;
    @FXML private TextArea bodyArea;

    @FXML private ComboBox<String> authTypeCombo;
    @FXML private javafx.scene.layout.GridPane bearerPane;
    @FXML private TextField tokenField;
    @FXML private javafx.scene.layout.GridPane basicPane;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML private Label statusLabel;
    @FXML private TextArea responseBody;
    @FXML private TableView<HeaderRow> responseHeaders;
    @FXML private TableColumn<HeaderRow, String> respHeaderKeyCol;
    @FXML private TableColumn<HeaderRow, String> respHeaderValueCol;

    // ── Injected dependencies ─────────────────────────────────────────────────
    private StorageService storage;
    private Supplier<String> envSupplier;
    private Runnable onCollectionModified;
    private Runnable onDirtyChanged;
    private Tab ownTab;

    // ── State ─────────────────────────────────────────────────────────────────
    private String currentCollection;
    private List<String> currentFolderPath = List.of();
    private String currentRequestName;
    private boolean isDirty = false;
    private boolean settingValues = false;

    private final ObservableList<HeaderRow> headerRows = FXCollections.observableArrayList();
    private final ObservableList<HeaderRow> responseHeaderRows = FXCollections.observableArrayList();

    private final HttpService httpService = new HttpService();
    private final InterpolationService interpolation = new InterpolationService();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────
    // FXML lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        for (HttpMethod m : HttpMethod.values()) methodCombo.getItems().add(m.name());
        methodCombo.setValue("GET");

        for (BodyType bt : BodyType.values()) bodyTypeCombo.getItems().add(bt.name());
        bodyTypeCombo.setValue("NONE");

        for (AuthType at : AuthType.values()) authTypeCombo.getItems().add(at.name());
        authTypeCombo.setValue("NONE");

        headersTable.setItems(headerRows);
        headerKeyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        headerValueCol.setCellValueFactory(c -> c.getValue().valueProperty());
        headerKeyCol.setCellFactory(headerCellFactory(HeaderRow::setKey));
        headerValueCol.setCellFactory(headerCellFactory(HeaderRow::setValue));

        responseHeaders.setItems(responseHeaderRows);
        respHeaderKeyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        respHeaderValueCol.setCellValueFactory(c -> c.getValue().valueProperty());

        updateBreadcrumb(null, List.of(), null);

        urlField.textProperty().addListener((o, p, n)       -> { if (!settingValues) markDirty(); });

        Tooltip urlTooltip = new Tooltip();
        urlTooltip.setShowDelay(Duration.millis(400));
        urlTooltip.setWrapText(true);
        urlTooltip.setMaxWidth(600);
        urlField.setOnMouseEntered(e -> refreshUrlTooltip(urlTooltip));
        methodCombo.valueProperty().addListener((o, p, n)   -> { if (!settingValues) markDirty(); });
        bodyArea.textProperty().addListener((o, p, n)       -> { if (!settingValues) markDirty(); });
        bodyTypeCombo.valueProperty().addListener((o, p, n) -> { if (!settingValues) markDirty(); });
        authTypeCombo.valueProperty().addListener((o, p, n) -> { if (!settingValues) markDirty(); });
        tokenField.textProperty().addListener((o, p, n)     -> { if (!settingValues) markDirty(); });
        usernameField.textProperty().addListener((o, p, n)  -> { if (!settingValues) markDirty(); });
        passwordField.textProperty().addListener((o, p, n)  -> { if (!settingValues) markDirty(); });
        headerRows.addListener((ListChangeListener<HeaderRow>) c -> { if (!settingValues) markDirty(); });
    }

    public void init(StorageService storage, Supplier<String> envSupplier,
                     Runnable onCollectionModified, Runnable onDirtyChanged, Tab ownTab) {
        this.storage = storage;
        this.envSupplier = envSupplier;
        this.onCollectionModified = onCollectionModified;
        this.onDirtyChanged = onDirtyChanged;
        this.ownTab = ownTab;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isDirty() { return isDirty; }
    public String getCurrentCollection() { return currentCollection; }
    public List<String> getCurrentFolderPath() { return currentFolderPath; }
    public String getCurrentRequestName() { return currentRequestName; }

    public boolean isShowing(String collection, List<String> folderPath, String requestName) {
        return Objects.equals(currentCollection, collection)
                && Objects.equals(currentFolderPath, folderPath)
                && Objects.equals(currentRequestName, requestName);
    }

    public boolean isRequestDirty(String collection, List<String> folderPath, String requestName) {
        return isDirty
                && Objects.equals(currentCollection, collection)
                && Objects.equals(currentFolderPath, folderPath)
                && Objects.equals(currentRequestName, requestName);
    }

    public void loadRequest(SavedRequest req, String collection, List<String> folderPath, String requestName) {
        loadRequestIntoEditor(req);
        currentCollection = collection;
        currentFolderPath = folderPath != null ? folderPath : List.of();
        currentRequestName = requestName;
        updateBreadcrumb(collection, currentFolderPath, requestName);
        updateTabTitle();
    }

    public void newRequest() {
        clearEditor();
        updateTabTitle();
    }

    public void applyEditorFont(AppConfig config) {
        String family = config.getEditorFontFamily() != null ? config.getEditorFontFamily() : "Consolas";
        int size = config.getEditorFontSize() > 0 ? config.getEditorFontSize() : 12;
        String style = String.format("-fx-font-family: '%s'; -fx-font-size: %dpx;", family, size);
        bodyArea.setStyle(style);
        responseBody.setStyle(style);
    }

    /** Prompts save/discard if dirty. Returns false if user cancelled. */
    public boolean promptSaveIfDirty() {
        if (!isDirty || currentRequestName == null) return true;
        ButtonType saveBtn    = new ButtonType("Save");
        ButtonType discardBtn = new ButtonType("Discard");
        ButtonType cancelBtn  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Save changes to \"" + currentRequestName + "\"?");
        alert.setContentText("Your edits will be lost if you don't save them.");
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancelBtn) return false;
        if (result.get() == saveBtn) saveCurrentRequest();
        return true;
    }

    public void notifyRequestRenamed(String newName) {
        currentRequestName = newName;
        updateBreadcrumb(currentCollection, currentFolderPath, currentRequestName);
        updateTabTitle();
    }

    public void notifyCollectionRenamed(String newName) {
        currentCollection = newName;
        updateBreadcrumb(currentCollection, currentFolderPath, currentRequestName);
    }

    public void notifyRequestMoved(String newCollection, List<String> newFolderPath) {
        currentCollection = newCollection;
        currentFolderPath = newFolderPath != null ? newFolderPath : List.of();
        updateBreadcrumb(currentCollection, currentFolderPath, currentRequestName);
    }

    /** Called when a folder on the path to this request is renamed. */
    public void notifyFolderRenamed(String collectionName, List<String> oldPath, List<String> newPath) {
        if (!Objects.equals(currentCollection, collectionName)) return;
        if (currentFolderPath.size() >= oldPath.size()
                && currentFolderPath.subList(0, oldPath.size()).equals(oldPath)) {
            List<String> suffix = currentFolderPath.subList(oldPath.size(), currentFolderPath.size());
            List<String> updated = new ArrayList<>(newPath);
            updated.addAll(suffix);
            currentFolderPath = List.copyOf(updated);
            updateBreadcrumb(currentCollection, currentFolderPath, currentRequestName);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request editor internals
    // ─────────────────────────────────────────────────────────────────────────

    private void loadRequestIntoEditor(SavedRequest req) {
        settingValues = true;
        try {
            methodCombo.setValue(req.getMethod() != null ? req.getMethod().name() : "GET");
            urlField.setText(req.getUrl() != null ? req.getUrl() : "");

            headerRows.clear();
            if (req.getHeaders() != null) {
                req.getHeaders().forEach((k, v) -> headerRows.add(new HeaderRow(k, v)));
            }

            bodyTypeCombo.setValue(req.getBodyType() != null ? req.getBodyType().name() : "NONE");
            bodyArea.setText(req.getBody() != null ? req.getBody() : "");

            AuthConfig auth = req.getAuth();
            if (auth != null && auth.getType() != null) {
                authTypeCombo.setValue(auth.getType().name());
                tokenField.setText(auth.getToken() != null ? auth.getToken() : "");
                usernameField.setText(auth.getUsername() != null ? auth.getUsername() : "");
                passwordField.setText(auth.getPassword() != null ? auth.getPassword() : "");
            } else {
                authTypeCombo.setValue("NONE");
                tokenField.clear();
                usernameField.clear();
                passwordField.clear();
            }
            updateAuthPaneVisibility();

            statusLabel.setText("");
            statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
            responseBody.clear();
            responseHeaderRows.clear();
        } finally {
            settingValues = false;
            clearDirty();
        }
    }

    private void clearEditor() {
        settingValues = true;
        try {
            currentCollection = null;
            currentFolderPath = List.of();
            currentRequestName = null;
            updateBreadcrumb(null, List.of(), null);
            methodCombo.setValue("GET");
            urlField.clear();
            headerRows.clear();
            bodyTypeCombo.setValue("NONE");
            bodyArea.clear();
            authTypeCombo.setValue("NONE");
            tokenField.clear();
            usernameField.clear();
            passwordField.clear();
            updateAuthPaneVisibility();
            statusLabel.setText("");
            statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
            responseBody.clear();
            responseHeaderRows.clear();
        } finally {
            settingValues = false;
            clearDirty();
        }
    }

    private SavedRequest buildRequestFromForm() {
        SavedRequest req = new SavedRequest();
        req.setMethod(HttpMethod.valueOf(methodCombo.getValue()));
        req.setUrl(urlField.getText().trim());

        Map<String, String> headers = new LinkedHashMap<>();
        for (HeaderRow row : headerRows) {
            if (!row.getKey().isBlank()) headers.put(row.getKey().trim(), row.getValue());
        }
        req.setHeaders(headers.isEmpty() ? null : headers);

        BodyType bodyType = BodyType.valueOf(bodyTypeCombo.getValue());
        req.setBodyType(bodyType);
        String body = bodyArea.getText();
        if (bodyType != BodyType.NONE && !body.isBlank()) req.setBody(body);

        AuthType authType = AuthType.valueOf(authTypeCombo.getValue());
        if (authType != AuthType.NONE) {
            AuthConfig auth = new AuthConfig();
            auth.setType(authType);
            if (authType == AuthType.BEARER) {
                auth.setToken(tokenField.getText());
            } else if (authType == AuthType.BASIC) {
                auth.setUsername(usernameField.getText());
                auth.setPassword(passwordField.getText());
            }
            req.setAuth(auth);
        }
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FXML handlers
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onAddHeader() {
        headerRows.add(new HeaderRow("", ""));
        headersTable.scrollTo(headerRows.size() - 1);
        headersTable.edit(headerRows.size() - 1, headerKeyCol);
    }

    @FXML
    private void onRemoveHeader() {
        HeaderRow selected = headersTable.getSelectionModel().getSelectedItem();
        if (selected != null) headerRows.remove(selected);
    }

    @FXML
    private void onAuthTypeChanged() {
        updateAuthPaneVisibility();
    }

    @FXML
    private void onSend() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) { showError("URL is required."); return; }

        SavedRequest req = buildRequestFromForm();
        Map<String, String> vars;
        try {
            String envName = envSupplier != null ? envSupplier.get() : null;
            vars = (envName != null) ? storage.resolveVariables(envName) : new HashMap<>();
        } catch (IOException e) {
            showError("Failed to load environment variables: " + e.getMessage());
            return;
        }

        SavedRequest resolved = interpolation.interpolate(req, vars);
        if (resolved.getUrl() != null && resolved.getUrl().contains("{{")) {
            Matcher m = VAR_TOKEN.matcher(resolved.getUrl());
            StringBuilder unresolved = new StringBuilder();
            while (m.find()) {
                if (!unresolved.isEmpty()) unresolved.append(", ");
                unresolved.append("{{").append(m.group(1)).append("}}");
            }
            showError("Unresolved variable(s) in URL: " + unresolved
                    + "\nCheck that the active environment defines " + (unresolved.toString().contains(",") ? "these variables" : "this variable") + ".");
            return;
        }
        sendButton.setDisable(true);
        statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
        statusLabel.getStyleClass().add("status-pending");
        statusLabel.setText("Sending…");
        responseBody.clear();
        responseHeaderRows.clear();

        Task<HttpResponse<String>> task = new Task<>() {
            @Override
            protected HttpResponse<String> call() throws Exception {
                return httpService.execute(resolved);
            }
        };

        task.setOnSucceeded(e -> {
            sendButton.setDisable(false);
            displayResponse(task.getValue());
        });

        task.setOnFailed(e -> {
            sendButton.setDisable(false);
            statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
            statusLabel.getStyleClass().add("status-error");
            Throwable ex = task.getException();
            statusLabel.setText(errorStatus(ex));
            responseBody.setText(errorDetail(ex));
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onSave() {
        if (currentCollection != null && currentRequestName != null) {
            saveCurrentRequest();
        } else {
            onSaveAs();
        }
    }

    @FXML
    private void onSaveAs() {
        List<String> collections;
        try {
            collections = storage.listCollectionNames();
        } catch (IOException e) {
            showError("Failed to list collections: " + e.getMessage());
            return;
        }
        if (collections.isEmpty()) {
            showError("No collections exist. Create one first using the + button.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Save Request");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 16, 6, 16));

        TextField nameField = new TextField(currentRequestName != null ? currentRequestName : "");
        nameField.setPromptText("Request name");
        nameField.setPrefWidth(260);

        ComboBox<String> colCombo = new ComboBox<>();
        colCombo.getItems().addAll(collections);
        colCombo.setValue(currentCollection != null && collections.contains(currentCollection)
                ? currentCollection : collections.get(0));
        colCombo.setPrefWidth(260);

        Callback<ListView<List<String>>, ListCell<List<String>>> folderCellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(List<String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : folderPathLabel(item));
            }
        };
        ComboBox<List<String>> folderCombo = new ComboBox<>();
        folderCombo.setPrefWidth(260);
        folderCombo.setCellFactory(folderCellFactory);
        folderCombo.setButtonCell(folderCellFactory.call(null));

        Runnable reloadFolders = () -> {
            String colName = colCombo.getValue();
            if (colName == null) return;
            try {
                RequestCollection col = storage.loadCollection(colName);
                List<List<String>> paths = StorageService.collectFolderPaths(col);
                folderCombo.getItems().setAll(paths);
                if (colName.equals(currentCollection) && paths.contains(currentFolderPath)) {
                    folderCombo.setValue(currentFolderPath);
                } else {
                    folderCombo.setValue(List.of());
                }
            } catch (IOException e) {
                folderCombo.getItems().setAll(List.of(List.of()));
                folderCombo.setValue(List.of());
            }
        };
        reloadFolders.run();
        colCombo.valueProperty().addListener((obs, o, n) -> reloadFolders.run());

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Collection:"), 0, 1);
        grid.add(colCombo, 1, 1);
        grid.add(new Label("Folder:"), 0, 2);
        grid.add(folderCombo, 1, 2);
        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(nameField.getText().isBlank());
        nameField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.isBlank()));

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String name = nameField.getText().trim();
            String colName = colCombo.getValue();
            if (name.isEmpty() || colName == null) return;
            List<String> targetFolderPath = folderCombo.getValue() != null ? folderCombo.getValue() : List.of();

            SavedRequest req = buildRequestFromForm();
            req.setName(name);
            try {
                RequestCollection col = storage.loadCollection(colName);
                StorageService.upsertRequest(col, targetFolderPath, req);
                storage.saveCollection(col);
                currentCollection = colName;
                currentFolderPath = targetFolderPath;
                currentRequestName = name;
                updateBreadcrumb(colName, targetFolderPath, name);
                clearDirty();
                updateTabTitle();
                if (onCollectionModified != null) onCollectionModified.run();
            } catch (IOException e) {
                showError("Failed to save request: " + e.getMessage());
            }
        });
    }

    private static String folderPathLabel(List<String> path) {
        return path.isEmpty() ? "(top level)" : String.join(" / ", path);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshUrlTooltip(Tooltip tooltip) {
        String text = urlField.getText();
        if (text == null || !text.contains("{{")) {
            urlField.setTooltip(null);
            return;
        }
        Map<String, String> vars = new HashMap<>();
        try {
            String envName = envSupplier != null ? envSupplier.get() : null;
            if (envName != null && storage != null) {
                vars = storage.resolveVariables(envName);
            }
        } catch (IOException ignored) {
        }
        String resolved = interpolation.interpolate(text, vars);
        tooltip.setText(resolved);
        urlField.setTooltip(tooltip);
    }

    private void markDirty() {
        if (!isDirty) {
            isDirty = true;
            updateTabTitle();
            if (onDirtyChanged != null) onDirtyChanged.run();
        }
        if (currentRequestName != null) {
            requestBreadcrumb.setStyle("-fx-text-fill: #e05a00;");
        }
    }

    private void clearDirty() {
        isDirty = false;
        requestBreadcrumb.setStyle("");
        updateTabTitle();
        if (onDirtyChanged != null) onDirtyChanged.run();
    }

    private void saveCurrentRequest() {
        if (currentCollection == null || currentRequestName == null) return;
        SavedRequest req = buildRequestFromForm();
        req.setName(currentRequestName);
        try {
            RequestCollection col = storage.loadCollection(currentCollection);
            StorageService.upsertRequest(col, currentFolderPath, req);
            storage.saveCollection(col);
            clearDirty();
            if (onCollectionModified != null) onCollectionModified.run();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void updateTabTitle() {
        if (ownTab == null) return;
        String name = currentRequestName != null ? currentRequestName : "New Request";
        ownTab.setText((isDirty ? "• " : "") + name);
    }

    private void updateBreadcrumb(String collection, List<String> folderPath, String request) {
        if (collection == null) {
            collectionBreadcrumb.setText("New Request");
            collectionBreadcrumb.getStyleClass().removeAll("breadcrumb-collection");
            if (!collectionBreadcrumb.getStyleClass().contains("breadcrumb-new"))
                collectionBreadcrumb.getStyleClass().add("breadcrumb-new");
            breadcrumbSep.setVisible(false);
            breadcrumbSep.setManaged(false);
            requestBreadcrumb.setVisible(false);
            requestBreadcrumb.setManaged(false);
        } else {
            String prefix = collection;
            if (folderPath != null && !folderPath.isEmpty()) {
                prefix = collection + " / " + String.join(" / ", folderPath);
            }
            collectionBreadcrumb.setText(prefix);
            collectionBreadcrumb.getStyleClass().removeAll("breadcrumb-new");
            if (!collectionBreadcrumb.getStyleClass().contains("breadcrumb-collection"))
                collectionBreadcrumb.getStyleClass().add("breadcrumb-collection");
            breadcrumbSep.setVisible(true);
            breadcrumbSep.setManaged(true);
            requestBreadcrumb.setText(request);
            requestBreadcrumb.setVisible(true);
            requestBreadcrumb.setManaged(true);
        }
    }

    private void updateAuthPaneVisibility() {
        String type = authTypeCombo.getValue();
        boolean bearer = "BEARER".equals(type);
        boolean basic  = "BASIC".equals(type);
        bearerPane.setVisible(bearer);
        bearerPane.setManaged(bearer);
        basicPane.setVisible(basic);
        basicPane.setManaged(basic);
    }

    private void displayResponse(HttpResponse<String> response) {
        int code = response.statusCode();
        statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
        statusLabel.getStyleClass().add(code >= 200 && code < 300 ? "status-ok" : "status-error");
        statusLabel.setText("HTTP " + code + " " + statusTextFor(code));

        String body = response.body();
        responseBody.setText((body == null || body.isBlank()) ? "(empty body)" : prettyJson(body));

        responseHeaderRows.clear();
        response.headers().map().forEach((k, values) ->
                values.forEach(v -> responseHeaderRows.add(new HeaderRow(k, v))));
    }

    private String prettyJson(String text) {
        try {
            Object parsed = jsonMapper.readValue(text, Object.class);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception ex) {
            return text;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static String errorStatus(Throwable ex) {
        if (ex instanceof HttpTimeoutException) return "Timed out";
        if (ex instanceof UnknownHostException)  return "Host not found";
        if (ex instanceof ConnectException)      return "Connection refused";
        return "Connection failed";
    }

    private static String errorDetail(Throwable ex) {
        if (ex == null) return "Unknown error";
        String msg = ex.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : ex.getClass().getSimpleName();
    }

    private static String statusTextFor(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default  -> "";
        };
    }

    private Callback<TableColumn<HeaderRow, String>, TableCell<HeaderRow, String>>
            headerCellFactory(BiConsumer<HeaderRow, String> setter) {
        return col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.focusedProperty().addListener((obs, was, focused) -> { if (!focused) commit(); });
                tf.setOnAction(e -> commit());
            }

            private void commit() {
                HeaderRow row = getTableRow() != null ? (HeaderRow) getTableRow().getItem() : null;
                if (row != null) {
                    setter.accept(row, tf.getText());
                    if (!settingValues) markDirty();
                }
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                if (!tf.isFocused()) tf.setText(value != null ? value : "");
                setGraphic(tf);
                setText(null);
            }
        };
    }
}
