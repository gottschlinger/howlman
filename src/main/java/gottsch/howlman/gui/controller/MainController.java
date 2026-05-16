package gottsch.howlman.gui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gottsch.howlman.model.AppConfig;
import gottsch.howlman.model.AuthConfig;
import gottsch.howlman.model.AuthType;
import gottsch.howlman.model.BodyType;
import gottsch.howlman.model.Collection;
import gottsch.howlman.model.Environment;
import gottsch.howlman.model.HttpMethod;
import gottsch.howlman.model.SavedRequest;
import gottsch.howlman.service.HttpService;
import gottsch.howlman.service.InterpolationService;
import gottsch.howlman.service.StorageService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Callback;

import javafx.collections.ListChangeListener;

import java.util.Optional;
import java.util.function.BiConsumer;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainController {

    // ── Breadcrumb ────────────────────────────────────────────────────────────
    @FXML private Label collectionBreadcrumb;
    @FXML private Label breadcrumbSep;
    @FXML private Label requestBreadcrumb;

    // ── Top bar ──────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> environmentCombo;
    @FXML private Button renameEnvButton;
    @FXML private Button editEnvButton;
    @FXML private Button deleteEnvButton;

    // ── Sidebar ──────────────────────────────────────────────────────────────
    @FXML private TreeView<String> collectionTree;

    // ── URL bar ──────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> methodCombo;
    @FXML private TextField urlField;
    @FXML private Button sendButton;

    // ── Headers tab ──────────────────────────────────────────────────────────
    @FXML private TableView<HeaderRow> headersTable;
    @FXML private TableColumn<HeaderRow, String> headerKeyCol;
    @FXML private TableColumn<HeaderRow, String> headerValueCol;

    // ── Body tab ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> bodyTypeCombo;
    @FXML private TextArea bodyArea;

    // ── Auth tab ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> authTypeCombo;
    @FXML private GridPane bearerPane;
    @FXML private TextField tokenField;
    @FXML private GridPane basicPane;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    // ── Response area ─────────────────────────────────────────────────────────
    @FXML private Label statusLabel;
    @FXML private TextArea responseBody;
    @FXML private TableView<HeaderRow> responseHeaders;
    @FXML private TableColumn<HeaderRow, String> respHeaderKeyCol;
    @FXML private TableColumn<HeaderRow, String> respHeaderValueCol;

    // ── Services ──────────────────────────────────────────────────────────────
    private StorageService storage;
    private final HttpService httpService = new HttpService();
    private final InterpolationService interpolation = new InterpolationService();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    // ── Current request state ─────────────────────────────────────────────────
    private String currentCollection;
    private String currentRequestName;
    private boolean isDirty = false;
    private boolean settingValues = false;
    private boolean changingSelection = false;
    private TreeItem<String> lastSelectedItem = null;

    // ── Observable data ───────────────────────────────────────────────────────
    private final ObservableList<HeaderRow> headerRows = FXCollections.observableArrayList();
    private final ObservableList<HeaderRow> responseHeaderRows = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────
    // FXML lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Method combo
        for (HttpMethod m : HttpMethod.values()) {
            methodCombo.getItems().add(m.name());
        }
        methodCombo.setValue("GET");

        // Body type combo
        for (BodyType bt : BodyType.values()) {
            bodyTypeCombo.getItems().add(bt.name());
        }
        bodyTypeCombo.setValue("NONE");

        // Auth type combo
        for (AuthType at : AuthType.values()) {
            authTypeCombo.getItems().add(at.name());
        }
        authTypeCombo.setValue("NONE");

        // Headers table — always-visible TextFields that commit on focus loss
        headersTable.setItems(headerRows);
        headerKeyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        headerValueCol.setCellValueFactory(c -> c.getValue().valueProperty());
        headerKeyCol.setCellFactory(headerCellFactory(HeaderRow::setKey));
        headerValueCol.setCellFactory(headerCellFactory(HeaderRow::setValue));

        // Response headers table
        responseHeaders.setItems(responseHeaderRows);
        respHeaderKeyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        respHeaderValueCol.setCellValueFactory(c -> c.getValue().valueProperty());

        // Env buttons start disabled until an environment is selected
        updateEnvButtonStates();
        environmentCombo.valueProperty().addListener((o, p, n) -> updateEnvButtonStates());

        // Initial breadcrumb state
        updateBreadcrumb(null, null);

        // Dirty tracking — any form change while not programmatically setting values
        urlField.textProperty().addListener((o, p, n)       -> { if (!settingValues) markDirty(); });
        methodCombo.valueProperty().addListener((o, p, n)   -> { if (!settingValues) markDirty(); });
        bodyArea.textProperty().addListener((o, p, n)       -> { if (!settingValues) markDirty(); });
        bodyTypeCombo.valueProperty().addListener((o, p, n) -> { if (!settingValues) markDirty(); });
        authTypeCombo.valueProperty().addListener((o, p, n) -> { if (!settingValues) markDirty(); });
        tokenField.textProperty().addListener((o, p, n)     -> { if (!settingValues) markDirty(); });
        usernameField.textProperty().addListener((o, p, n)  -> { if (!settingValues) markDirty(); });
        passwordField.textProperty().addListener((o, p, n)  -> { if (!settingValues) markDirty(); });
        headerRows.addListener((ListChangeListener<HeaderRow>) c -> { if (!settingValues) markDirty(); });

        // Tree
        collectionTree.setShowRoot(false);
        collectionTree.setRoot(new TreeItem<>());
        collectionTree.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> onTreeSelectionChanged(sel));
        setupTreeContextMenu();
    }

    /** Called by HowlManApp after FXML is loaded, to inject the storage service. */
    public void init(StorageService storage) throws IOException {
        this.storage = storage;
        refreshEnvironments();
        refreshCollections();
        applyEditorFont(storage.loadConfig());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Environment
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshEnvironments() throws IOException {
        String current = environmentCombo.getValue();
        environmentCombo.getItems().setAll(storage.listEnvironmentNames());
        AppConfig config = storage.loadConfig();
        String active = config.getActiveEnvironment();
        if (active != null && environmentCombo.getItems().contains(active)) {
            environmentCombo.setValue(active);
        } else if (current != null && environmentCombo.getItems().contains(current)) {
            environmentCombo.setValue(current);
        }
        updateEnvButtonStates();
    }

    private void updateEnvButtonStates() {
        boolean hasEnv = environmentCombo.getValue() != null;
        if (renameEnvButton != null) renameEnvButton.setDisable(!hasEnv);
        if (editEnvButton   != null) editEnvButton.setDisable(!hasEnv);
        if (deleteEnvButton != null) deleteEnvButton.setDisable(!hasEnv);
    }

    @FXML
    private void onEnvironmentChanged() {
        String selected = environmentCombo.getValue();
        if (selected == null || storage == null) return;
        try {
            AppConfig config = storage.loadConfig();
            config.setActiveEnvironment(selected);
            storage.saveConfig(config);
        } catch (IOException e) {
            showError("Failed to save active environment: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collections tree
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshCollections() throws IOException {
        TreeItem<String> root = collectionTree.getRoot();
        root.getChildren().clear();
        for (String name : storage.listCollectionNames()) {
            TreeItem<String> colItem = new TreeItem<>(name);
            colItem.setExpanded(true);
            Collection col = storage.loadCollection(name);
            if (col.getRequests() != null) {
                for (SavedRequest req : col.getRequests()) {
                    colItem.getChildren().add(new TreeItem<>(req.getName()));
                }
            }
            root.getChildren().add(colItem);
        }
    }

    private void onTreeSelectionChanged(TreeItem<String> selected) {
        if (changingSelection) return;
        if (selected == null) return;
        TreeItem<String> parent = selected.getParent();
        // Only load if it's a request node (parent is a collection node, not root)
        if (parent == null || parent.getValue() == null) return;

        // Prompt if the current request has unsaved changes
        if (isDirty && currentRequestName != null) {
            ButtonType saveBtn    = new ButtonType("Save");
            ButtonType discardBtn = new ButtonType("Discard");
            ButtonType cancelBtn  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("Save changes to \"" + currentRequestName + "\"?");
            alert.setContentText("Your edits will be lost if you don't save them.");
            alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isEmpty() || result.get() == cancelBtn) {
                changingSelection = true;
                if (lastSelectedItem != null) collectionTree.getSelectionModel().select(lastSelectedItem);
                else                          collectionTree.getSelectionModel().clearSelection();
                changingSelection = false;
                return;
            }
            if (result.get() == saveBtn) {
                saveCurrentRequest();
            }
            // Discard: fall through and load the new request
        }

        String requestName = selected.getValue();
        String collectionName = parent.getValue();
        try {
            Collection col = storage.loadCollection(collectionName);
            if (col.getRequests() == null) return;
            col.getRequests().stream()
                    .filter(r -> r.getName().equals(requestName))
                    .findFirst()
                    .ifPresent(req -> {
                        loadRequestIntoEditor(req);
                        currentCollection = collectionName;
                        currentRequestName = requestName;
                        updateBreadcrumb(collectionName, requestName);
                    });
            lastSelectedItem = selected;
        } catch (IOException e) {
            showError("Failed to load request: " + e.getMessage());
        }
    }

    @FXML
    private void onNewCollection() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Collection");
        dlg.setHeaderText(null);
        dlg.setContentText("Collection name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            try {
                Collection col = new Collection();
                col.setName(name);
                col.setRequests(new ArrayList<>());
                storage.saveCollection(col);
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to create collection: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request editor
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
            currentRequestName = null;
            lastSelectedItem = null;
            updateBreadcrumb(null, null);
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
            if (!row.getKey().isBlank()) {
                headers.put(row.getKey().trim(), row.getValue());
            }
        }
        req.setHeaders(headers.isEmpty() ? null : headers);

        BodyType bodyType = BodyType.valueOf(bodyTypeCombo.getValue());
        req.setBodyType(bodyType);
        String body = bodyArea.getText();
        if (bodyType != BodyType.NONE && !body.isBlank()) {
            req.setBody(body);
        }

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
    // Headers tab actions
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
        if (selected != null) {
            headerRows.remove(selected);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth tab
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onAuthTypeChanged() {
        updateAuthPaneVisibility();
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

    // ─────────────────────────────────────────────────────────────────────────
    // Send
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onSend() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            showError("URL is required.");
            return;
        }

        SavedRequest req = buildRequestFromForm();

        Map<String, String> vars;
        try {
            String envName = environmentCombo.getValue();
            vars = (envName != null) ? storage.resolveVariables(envName) : new HashMap<>();
        } catch (IOException e) {
            showError("Failed to load environment variables: " + e.getMessage());
            return;
        }

        SavedRequest resolved = interpolation.interpolate(req, vars);

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
            statusLabel.setText("Connection failed");
            Throwable ex = task.getException();
            responseBody.setText(ex != null ? ex.getMessage() : "Unknown error");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void displayResponse(HttpResponse<String> response) {
        int code = response.statusCode();
        statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-pending");
        statusLabel.getStyleClass().add(code >= 200 && code < 300 ? "status-ok" : "status-error");
        statusLabel.setText("HTTP " + code + " " + statusTextFor(code));

        String body = response.body();
        if (body == null || body.isBlank()) {
            responseBody.setText("(empty body)");
        } else {
            responseBody.setText(prettyJson(body));
        }

        responseHeaderRows.clear();
        response.headers().map().forEach((k, values) ->
                values.forEach(v -> responseHeaderRows.add(new HeaderRow(k, v))));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save request dialog
    // ─────────────────────────────────────────────────────────────────────────

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

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Collection:"), 0, 1);
        grid.add(colCombo, 1, 1);
        dialog.getDialogPane().setContent(grid);

        // Disable OK until name is non-blank
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(nameField.getText().isBlank());
        nameField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.isBlank()));

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String name = nameField.getText().trim();
            String colName = colCombo.getValue();
            if (name.isEmpty() || colName == null) return;

            SavedRequest req = buildRequestFromForm();
            req.setName(name);
            try {
                Collection col = storage.loadCollection(colName);
                List<SavedRequest> requests = new ArrayList<>(
                        col.getRequests() != null ? col.getRequests() : List.of());
                upsertRequest(requests, req);
                col.setRequests(requests);
                storage.saveCollection(col);
                refreshCollections();
                currentCollection = colName;
                currentRequestName = name;
                updateBreadcrumb(colName, name);
                clearDirty();
            } catch (IOException e) {
                showError("Failed to save request: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tree context menu
    // ─────────────────────────────────────────────────────────────────────────

    private void setupTreeContextMenu() {
        collectionTree.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    // Highlight the currently dirty request in orange
                    TreeItem<String> ti = getTreeItem();
                    boolean isDirtyCell = isDirty
                            && ti != null
                            && ti.getParent() != null
                            && ti.getParent().getValue() != null
                            && item.equals(currentRequestName)
                            && ti.getParent().getValue().equals(currentCollection);
                    setStyle(isDirtyCell ? "-fx-text-fill: #e05a00; -fx-font-style: italic;" : "");
                }
            };

            cell.setOnContextMenuRequested(event -> {
                TreeItem<String> treeItem = cell.getTreeItem();
                if (treeItem == null) return;

                ContextMenu menu = new ContextMenu();
                boolean isCollection = (treeItem.getParent() == collectionTree.getRoot());

                if (isCollection) {
                    MenuItem newReq = new MenuItem("New Request");
                    newReq.setOnAction(e -> clearEditor());
                    MenuItem renameCol = new MenuItem("Rename…");
                    renameCol.setOnAction(e -> onRenameCollection(treeItem.getValue()));
                    MenuItem deleteCol = new MenuItem("Delete Collection");
                    deleteCol.setOnAction(e -> onDeleteCollection(treeItem.getValue()));
                    menu.getItems().addAll(newReq, renameCol, deleteCol);
                } else {
                    String colName = treeItem.getParent().getValue();
                    MenuItem renameReq = new MenuItem("Rename…");
                    renameReq.setOnAction(e -> onRenameRequest(colName, treeItem.getValue()));
                    MenuItem deleteReq = new MenuItem("Delete Request");
                    deleteReq.setOnAction(e -> onDeleteRequest(colName, treeItem.getValue()));
                    menu.getItems().addAll(renameReq, deleteReq);
                }

                menu.show(cell, event.getScreenX(), event.getScreenY());
                event.consume();
            });

            return cell;
        });
    }

    private void onDeleteCollection(String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Collection");
        alert.setHeaderText("Delete \"" + name + "\"?");
        alert.setContentText("All saved requests in this collection will be removed.");
        alert.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                storage.deleteCollection(name);
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to delete collection: " + e.getMessage());
            }
        });
    }

    private void onDeleteRequest(String collectionName, String requestName) {
        try {
            Collection col = storage.loadCollection(collectionName);
            List<SavedRequest> requests = new ArrayList<>(
                    col.getRequests() != null ? col.getRequests() : List.of());
            requests.removeIf(r -> r.getName().equals(requestName));
            col.setRequests(requests);
            storage.saveCollection(col);
            refreshCollections();
        } catch (IOException e) {
            showError("Failed to delete request: " + e.getMessage());
        }
    }

    private void onRenameRequest(String collectionName, String oldName) {
        TextInputDialog dlg = new TextInputDialog(oldName);
        dlg.setTitle("Rename Request");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(raw -> {
            final String name = raw.trim();
            if (name.isBlank() || name.equals(oldName)) return;
            try {
                Collection col = storage.loadCollection(collectionName);
                List<SavedRequest> requests = new ArrayList<>(
                        col.getRequests() != null ? col.getRequests() : List.of());
                boolean conflict = requests.stream().anyMatch(r -> r.getName().equals(name));
                if (conflict) { showError("A request named \"" + name + "\" already exists."); return; }
                requests.stream().filter(r -> r.getName().equals(oldName))
                        .findFirst().ifPresent(r -> r.setName(name));
                col.setRequests(requests);
                storage.saveCollection(col);
                if (oldName.equals(currentRequestName) && collectionName.equals(currentCollection)) {
                    currentRequestName = name;
                    updateBreadcrumb(currentCollection, currentRequestName);
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to rename request: " + e.getMessage());
            }
        });
    }

    private void onRenameCollection(String oldName) {
        TextInputDialog dlg = new TextInputDialog(oldName);
        dlg.setTitle("Rename Collection");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(raw -> {
            final String name = raw.trim();
            if (name.isBlank() || name.equals(oldName)) return;
            try {
                List<String> existing = storage.listCollectionNames();
                if (existing.contains(name)) { showError("A collection named \"" + name + "\" already exists."); return; }
                Collection col = storage.loadCollection(oldName);
                col.setName(name);
                storage.saveCollection(col);
                storage.deleteCollection(oldName);
                if (oldName.equals(currentCollection)) {
                    currentCollection = name;
                    updateBreadcrumb(currentCollection, currentRequestName);
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to rename collection: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onSettings() {
        AppConfig config;
        try {
            config = storage.loadConfig();
        } catch (IOException e) {
            showError("Failed to load config: " + e.getMessage());
            return;
        }

        String currentFamily = config.getEditorFontFamily() != null
                ? config.getEditorFontFamily() : "Consolas";
        int currentSize = config.getEditorFontSize() > 0 ? config.getEditorFontSize() : 12;

        ComboBox<String> familyCombo = new ComboBox<>();
        familyCombo.getItems().addAll(Font.getFamilies());
        familyCombo.setValue(currentFamily);
        familyCombo.setPrefWidth(260);

        Spinner<Integer> sizeSpinner = new Spinner<>(8, 48, currentSize);
        sizeSpinner.setEditable(true);
        sizeSpinner.setPrefWidth(80);

        Label preview = new Label("The quick brown fox jumps over the lazy dog  0123456789");
        preview.setStyle(String.format("-fx-font-family: '%s'; -fx-font-size: %dpx; -fx-padding: 8 4 4 4;",
                currentFamily, currentSize));
        preview.setWrapText(true);

        familyCombo.valueProperty().addListener((o, p, n) ->
                preview.setStyle(String.format("-fx-font-family: '%s'; -fx-font-size: %dpx; -fx-padding: 8 4 4 4;",
                        n, sizeSpinner.getValue())));
        sizeSpinner.valueProperty().addListener((o, p, n) ->
                preview.setStyle(String.format("-fx-font-family: '%s'; -fx-font-size: %dpx; -fx-padding: 8 4 4 4;",
                        familyCombo.getValue(), n)));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 16, 8, 16));
        grid.add(new Label("Font family:"), 0, 0);
        grid.add(familyCombo, 1, 0);
        grid.add(new Label("Font size:"), 0, 1);
        grid.add(sizeSpinner, 1, 1);
        grid.add(preview, 0, 2, 2, 1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editor Font Settings");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/gottsch/howlman/gui/style.css").toExternalForm());

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            config.setEditorFontFamily(familyCombo.getValue());
            config.setEditorFontSize(sizeSpinner.getValue());
            try {
                storage.saveConfig(config);
                applyEditorFont(config);
            } catch (IOException e) {
                showError("Failed to save settings: " + e.getMessage());
            }
        });
    }

    private void applyEditorFont(AppConfig config) {
        String family = config.getEditorFontFamily() != null ? config.getEditorFontFamily() : "Consolas";
        int size = config.getEditorFontSize() > 0 ? config.getEditorFontSize() : 12;
        String style = String.format("-fx-font-family: '%s'; -fx-font-size: %dpx;", family, size);
        bodyArea.setStyle(style);
        responseBody.setStyle(style);
    }

    @FXML
    private void onNewEnvironment() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Environment");
        dlg.setHeaderText(null);
        dlg.setContentText("Environment name:");
        dlg.showAndWait().ifPresent(raw -> {
            final String name = raw.trim();
            if (name.isBlank()) return;
            try {
                if (storage.listEnvironmentNames().contains(name)) {
                    showError("An environment named \"" + name + "\" already exists.");
                    return;
                }
                storage.saveEnvironment(new Environment(name));
                refreshEnvironments();
                environmentCombo.setValue(name);
            } catch (IOException e) {
                showError("Failed to create environment: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteEnvironment() {
        String envName = environmentCombo.getValue();
        if (envName == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Environment");
        alert.setHeaderText("Delete \"" + envName + "\"?");
        alert.setContentText("All variables in this environment will be removed.");
        alert.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                storage.deleteEnvironment(envName);
                AppConfig config = storage.loadConfig();
                if (envName.equals(config.getActiveEnvironment())) {
                    config.setActiveEnvironment(null);
                    storage.saveConfig(config);
                }
                refreshEnvironments();
            } catch (IOException e) {
                showError("Failed to delete environment: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onEditEnvironment() {
        String envName = environmentCombo.getValue();
        if (envName == null) return;
        Environment env;
        try {
            env = storage.loadEnvironment(envName);
        } catch (IOException e) {
            showError("Failed to load environment: " + e.getMessage());
            return;
        }

        ObservableList<HeaderRow> varRows = FXCollections.observableArrayList();
        if (env.getVariables() != null) {
            env.getVariables().forEach((k, v) -> varRows.add(new HeaderRow(k, v)));
        }

        TableView<HeaderRow> table = new TableView<>(varRows);
        table.setEditable(true);
        table.setPrefHeight(300);

        TableColumn<HeaderRow, String> keyCol = new TableColumn<>("Variable");
        keyCol.setPrefWidth(180);
        keyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        keyCol.setCellFactory(headerCellFactory(HeaderRow::setKey, () -> {}));

        TableColumn<HeaderRow, String> valCol = new TableColumn<>("Value");
        valCol.setPrefWidth(260);
        valCol.setCellValueFactory(c -> c.getValue().valueProperty());
        valCol.setCellFactory(headerCellFactory(HeaderRow::setValue, () -> {}));

        table.getColumns().setAll(keyCol, valCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("small-button");
        addBtn.setOnAction(e -> {
            varRows.add(new HeaderRow("", ""));
            table.scrollTo(varRows.size() - 1);
        });

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("small-button");
        removeBtn.setOnAction(e -> {
            HeaderRow sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) varRows.remove(sel);
        });

        HBox btnBar = new HBox(6, addBtn, removeBtn);
        btnBar.setStyle("-fx-padding: 4 6 4 6;");

        VBox content = new VBox(table, btnBar);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Environment: " + envName);
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(480);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/gottsch/howlman/gui/style.css").toExternalForm());

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            Map<String, String> vars = new LinkedHashMap<>();
            for (HeaderRow row : varRows) {
                if (!row.getKey().isBlank()) {
                    vars.put(row.getKey().trim(), row.getValue());
                }
            }
            env.setVariables(vars);
            try {
                storage.saveEnvironment(env);
            } catch (IOException e) {
                showError("Failed to save environment: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onRenameEnvironment() {
        String oldName = environmentCombo.getValue();
        if (oldName == null) { showError("Select an environment to rename."); return; }
        TextInputDialog dlg = new TextInputDialog(oldName);
        dlg.setTitle("Rename Environment");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(raw -> {
            final String name = raw.trim();
            if (name.isBlank() || name.equals(oldName)) return;
            try {
                List<String> existing = storage.listEnvironmentNames();
                if (existing.contains(name)) { showError("An environment named \"" + name + "\" already exists."); return; }
                var env = storage.loadEnvironment(oldName);
                env.setName(name);
                storage.saveEnvironment(env);
                storage.deleteEnvironment(oldName);
                AppConfig config = storage.loadConfig();
                if (oldName.equals(config.getActiveEnvironment())) {
                    config.setActiveEnvironment(name);
                    storage.saveConfig(config);
                }
                refreshEnvironments();
            } catch (IOException e) {
                showError("Failed to rename environment: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void markDirty() {
        if (!isDirty) {
            isDirty = true;
            collectionTree.refresh();
        }
        if (currentRequestName != null) {
            requestBreadcrumb.setStyle("-fx-text-fill: #e05a00;");
        }
    }

    private void clearDirty() {
        isDirty = false;
        requestBreadcrumb.setStyle("");
        collectionTree.refresh();
    }

    private void saveCurrentRequest() {
        if (currentCollection == null || currentRequestName == null) return;
        SavedRequest req = buildRequestFromForm();
        req.setName(currentRequestName);
        try {
            Collection col = storage.loadCollection(currentCollection);
            List<SavedRequest> requests = new ArrayList<>(
                    col.getRequests() != null ? col.getRequests() : List.of());
            upsertRequest(requests, req);
            col.setRequests(requests);
            storage.saveCollection(col);
            clearDirty();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void updateBreadcrumb(String collection, String request) {
        if (collection == null) {
            collectionBreadcrumb.setText("New Request");
            collectionBreadcrumb.getStyleClass().removeAll("breadcrumb-collection");
            if (!collectionBreadcrumb.getStyleClass().contains("breadcrumb-new")) {
                collectionBreadcrumb.getStyleClass().add("breadcrumb-new");
            }
            breadcrumbSep.setVisible(false);
            breadcrumbSep.setManaged(false);
            requestBreadcrumb.setVisible(false);
            requestBreadcrumb.setManaged(false);
        } else {
            collectionBreadcrumb.setText(collection);
            collectionBreadcrumb.getStyleClass().removeAll("breadcrumb-new");
            if (!collectionBreadcrumb.getStyleClass().contains("breadcrumb-collection")) {
                collectionBreadcrumb.getStyleClass().add("breadcrumb-collection");
            }
            breadcrumbSep.setVisible(true);
            breadcrumbSep.setManaged(true);
            requestBreadcrumb.setText(request);
            requestBreadcrumb.setVisible(true);
            requestBreadcrumb.setManaged(true);
        }
    }

    @FXML
    private void onClearEditor() {
        clearEditor();
    }

    /** Replace an existing request with the same name in-place, or append if new. */
    private static void upsertRequest(List<SavedRequest> requests, SavedRequest req) {
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).getName().equals(req.getName())) {
                requests.set(i, req);
                return;
            }
        }
        requests.add(req);
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

    // ─────────────────────────────────────────────────────────────────────────
    // Cell factory — always-visible TextField, commits on focus loss
    // ─────────────────────────────────────────────────────────────────────────

    private Callback<TableColumn<HeaderRow, String>, TableCell<HeaderRow, String>>
            headerCellFactory(BiConsumer<HeaderRow, String> setter) {
        return headerCellFactory(setter, () -> { if (!settingValues) markDirty(); });
    }

    private Callback<TableColumn<HeaderRow, String>, TableCell<HeaderRow, String>>
            headerCellFactory(BiConsumer<HeaderRow, String> setter, Runnable onChange) {
        return col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.focusedProperty().addListener((obs, was, focused) -> {
                    if (!focused) commit();
                });
                tf.setOnAction(e -> commit());
            }

            private void commit() {
                HeaderRow row = getTableRow() != null ? (HeaderRow) getTableRow().getItem() : null;
                if (row != null) {
                    setter.accept(row, tf.getText());
                    onChange.run();
                }
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                if (!tf.isFocused()) {
                    tf.setText(value != null ? value : "");
                }
                setGraphic(tf);
                setText(null);
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HeaderRow — observable model for the headers TableView
    // ─────────────────────────────────────────────────────────────────────────

    public static class HeaderRow {
        private final SimpleStringProperty key;
        private final SimpleStringProperty value;

        public HeaderRow(String key, String value) {
            this.key   = new SimpleStringProperty(key);
            this.value = new SimpleStringProperty(value);
        }

        public StringProperty keyProperty()   { return key; }
        public StringProperty valueProperty() { return value; }
        public String getKey()   { return key.get(); }
        public String getValue() { return value.get(); }
        public void setKey(String v)   { key.set(v); }
        public void setValue(String v) { value.set(v); }
    }
}
