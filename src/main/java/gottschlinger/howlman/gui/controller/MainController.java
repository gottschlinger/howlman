package gottschlinger.howlman.gui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.AppConfig;
import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.model.HttpMethod;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.RequestFolder;
import gottschlinger.howlman.model.SavedRequest;
import gottschlinger.howlman.service.ImportExportService;
import gottschlinger.howlman.service.ImportResult;
import gottschlinger.howlman.service.InsomniaImporter;
import gottschlinger.howlman.service.NativeImporter;
import gottschlinger.howlman.service.PostmanImporter;
import gottschlinger.howlman.service.StorageService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class MainController {

    // ── Top bar ──────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> environmentCombo;
    @FXML private Button renameEnvButton;
    @FXML private Button editEnvButton;
    @FXML private Button deleteEnvButton;

    // ── Sidebar ──────────────────────────────────────────────────────────────
    @FXML private TreeView<TreeNodeData> collectionTree;

    // ── Tab pane ─────────────────────────────────────────────────────────────
    @FXML private TabPane requestTabPane;

    // ── Services ──────────────────────────────────────────────────────────────
    private StorageService storage;

    // ── Tab management ────────────────────────────────────────────────────────
    private Tab addTab;

    // ── Drag-and-drop ─────────────────────────────────────────────────────────
    private TreeItem<TreeNodeData> dragSourceItem;

    // ─────────────────────────────────────────────────────────────────────────
    // FXML lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        updateEnvButtonStates();
        environmentCombo.valueProperty().addListener((o, p, n) -> updateEnvButtonStates());

        collectionTree.setShowRoot(false);
        collectionTree.setRoot(new TreeItem<>());
        setupTreeContextMenu();

        // Phantom + tab — always last, never closeable
        addTab = new Tab("+");
        addTab.setClosable(false);
        requestTabPane.getTabs().add(addTab);
        requestTabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == addTab && storage != null) openNewTab();
        });
    }

    /** Called by HowlManApp after FXML is loaded. */
    public void init(StorageService storage) throws IOException {
        this.storage = storage;
        refreshEnvironments();
        refreshCollections();
        openNewTab();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab management
    // ─────────────────────────────────────────────────────────────────────────

    private Tab openNewTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/gottschlinger/howlman/gui/request_tab.fxml"));
            SplitPane content = loader.load();
            RequestTabController ctrl = loader.getController();

            Tab tab = new Tab("New Request");
            tab.setContent(content);
            tab.setUserData(ctrl);

            AppConfig config = storage.loadConfig();
            ctrl.init(storage,
                    () -> environmentCombo.getValue(),
                    this::onCollectionModified,
                    () -> collectionTree.refresh(),
                    tab);
            ctrl.applyEditorFont(config);

            // Insert before the + tab
            int insertIdx = requestTabPane.getTabs().indexOf(addTab);
            requestTabPane.getTabs().add(insertIdx, tab);

            tab.setOnCloseRequest(event -> {
                if (!ctrl.promptSaveIfDirty()) event.consume();
            });

            requestTabPane.getSelectionModel().select(tab);
            return tab;
        } catch (IOException e) {
            showError("Failed to open new tab: " + e.getMessage());
            return null;
        }
    }

    private RequestTabController currentTabController() {
        Tab selected = requestTabPane.getSelectionModel().getSelectedItem();
        if (selected == null || selected == addTab) return null;
        return (RequestTabController) selected.getUserData();
    }

    private List<RequestTabController> getTabControllers() {
        return requestTabPane.getTabs().stream()
                .filter(t -> t != addTab && t.getUserData() instanceof RequestTabController)
                .map(t -> (RequestTabController) t.getUserData())
                .collect(Collectors.toList());
    }

    private void openRequestInNewTab(String collectionName, List<String> folderPath, String requestName) {
        // Switch to existing tab if already open
        for (Tab tab : requestTabPane.getTabs()) {
            if (tab == addTab) continue;
            RequestTabController ctrl = (RequestTabController) tab.getUserData();
            if (ctrl.isShowing(collectionName, folderPath, requestName)) {
                requestTabPane.getSelectionModel().select(tab);
                return;
            }
        }
        // Open in a new tab
        try {
            RequestCollection col = storage.loadCollection(collectionName);
            StorageService.findRequest(col, folderPath, requestName).ifPresent(req -> {
                Tab tab = openNewTab();
                if (tab != null) {
                    RequestTabController ctrl = (RequestTabController) tab.getUserData();
                    ctrl.loadRequest(req, collectionName, folderPath, requestName);
                }
            });
        } catch (IOException e) {
            showError("Failed to load request: " + e.getMessage());
        }
    }

    private void onCollectionModified() {
        try {
            refreshCollections();
        } catch (IOException e) {
            showError("Failed to refresh collections: " + e.getMessage());
        }
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
        TreeItem<TreeNodeData> root = collectionTree.getRoot();
        root.getChildren().clear();
        for (String name : storage.listCollectionNames()) {
            RequestCollection col = storage.loadCollection(name);
            TreeItem<TreeNodeData> colItem = new TreeItem<>(new TreeNodeData.Collection(name), makeFolderIcon());
            colItem.setExpanded(true);
            addRequestItems(colItem, col.getRequests(), name, List.of());
            addFolderItems(colItem, col.getFolders(), name, List.of());
            root.getChildren().add(colItem);
        }
    }

    private void addRequestItems(TreeItem<TreeNodeData> parent, List<SavedRequest> requests,
                                 String colName, List<String> folderPath) {
        if (requests == null) return;
        for (SavedRequest req : requests) {
            parent.getChildren().add(new TreeItem<>(
                    new TreeNodeData.Request(req.getName(), colName, folderPath),
                    makeMethodBadge(req.getMethod())));
        }
    }

    private void addFolderItems(TreeItem<TreeNodeData> parent, List<RequestFolder> folders,
                                String colName, List<String> parentPath) {
        if (folders == null) return;
        for (RequestFolder folder : folders) {
            List<String> path = new ArrayList<>(parentPath);
            path.add(folder.getName());
            List<String> immutablePath = List.copyOf(path);
            TreeItem<TreeNodeData> folderItem = new TreeItem<>(
                    new TreeNodeData.Folder(folder.getName(), colName, immutablePath),
                    makeFolderIcon());
            folderItem.setExpanded(true);
            addRequestItems(folderItem, folder.getRequests(), colName, immutablePath);
            addFolderItems(folderItem, folder.getFolders(), colName, immutablePath);
            parent.getChildren().add(folderItem);
        }
    }

    private SVGPath makeFolderIcon() {
        SVGPath svg = new SVGPath();
        svg.setContent("M9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a2 2 0 0 1 .328-1.181L.5 3A2 2 0 0 1 2.5 1h4.828a2 2 0 0 1 1.414.586L9.828 3zm-2.95-.5a1 1 0 0 0-.707.293L5.828 3H2.5a1 1 0 0 0-1 .981l.006.139C1.88 4.709 3 3.5 3 3.5h13l-.006-.139A1 1 0 0 0 14.994 3H10.17l-.585-.586A1 1 0 0 0 8.878 2H6.878z");
        svg.setFill(Color.web("#F5A623"));
        svg.setScaleX(0.9);
        svg.setScaleY(0.9);
        return svg;
    }

    private Label makeMethodBadge(HttpMethod method) {
        String text;
        String bg;
        if (method == null) {
            text = "???"; bg = "#8b949e";
        } else {
            switch (method) {
                case GET    -> { text = "GET";    bg = "#28a745"; }
                case POST   -> { text = "POST";   bg = "#fd7e14"; }
                case PUT    -> { text = "PUT";    bg = "#007bff"; }
                case DELETE -> { text = "DELETE"; bg = "#dc3545"; }
                case PATCH  -> { text = "PATCH";  bg = "#6f42c1"; }
                default     -> { text = method.name(); bg = "#8b949e"; }
            }
        }
        Label badge = new Label(text);
        badge.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 9px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 1 4 1 4;" +
            "-fx-background-radius: 3;"
        );
        return badge;
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
                RequestCollection col = new RequestCollection();
                col.setName(name);
                col.setRequests(new ArrayList<>());
                storage.saveCollection(col);
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to create collection: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onImportCollection() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Collection or Environment");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File selected = fc.showOpenDialog(collectionTree.getScene().getWindow());
        if (selected == null) return;

        try {
            ImportResult result = autoDetect(selected.toPath());
            ImportExportService service = new ImportExportService();
            List<String> existingCols = storage.listCollectionNames();
            List<String> existingEnvs = storage.listEnvironmentNames();
            List<String> summary = new ArrayList<>();

            for (RequestCollection c : result.getCollections()) {
                String saveName = service.nextAvailableName(c.getName(), existingCols);
                existingCols.add(saveName);
                c.setName(saveName);
                storage.saveCollection(c);
                summary.add("Collection: " + saveName);
            }
            for (Environment e : result.getEnvironments()) {
                String saveName = service.nextAvailableName(e.getName(), existingEnvs);
                existingEnvs.add(saveName);
                e.setName(saveName);
                storage.saveEnvironment(e);
                summary.add("Environment: " + saveName);
            }

            refreshCollections();
            refreshEnvironments();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Import Complete");
            alert.setHeaderText(null);
            alert.setContentText(summary.isEmpty() ? "Nothing imported." : "Imported:\n" + String.join("\n", summary));
            alert.showAndWait();
        } catch (IOException e) {
            showError("Import failed: " + e.getMessage());
        }
    }

    @FXML
    private void onExportCollection() {
        List<String> colNames;
        try {
            colNames = storage.listCollectionNames();
        } catch (IOException e) {
            showError("Failed to list collections: " + e.getMessage());
            return;
        }
        if (colNames.isEmpty()) {
            showError("No collections to export.");
            return;
        }

        RequestTabController tab = currentTabController();
        String currentCol = tab != null ? tab.getCurrentCollection() : null;

        ComboBox<String> colCombo = new ComboBox<>();
        colCombo.getItems().addAll(colNames);
        colCombo.setValue(currentCol != null && colNames.contains(currentCol)
                ? currentCol : colNames.get(0));
        colCombo.setPrefWidth(240);

        ComboBox<String> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("HowlMan", "Postman", "Insomnia");
        formatCombo.setValue("HowlMan");
        formatCombo.setPrefWidth(160);

        Label fileLabel = new Label("(no file selected)");
        fileLabel.setStyle("-fx-text-fill: #888;");
        Path[] outputPath = {null};

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Export Collection");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);

        Button browseBtn = new Button("Choose file…");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export To");
            String prefix = formatCombo.getValue().toLowerCase() + "_";
            fc.setInitialFileName(prefix + colCombo.getValue() + ".json");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File f = fc.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                outputPath[0] = f.toPath();
                fileLabel.setText(f.getName());
                fileLabel.setStyle("");
                okBtn.setDisable(false);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 16, 8, 16));
        CheckBox includeEnvsBox = new CheckBox("Include environments");
        includeEnvsBox.setSelected(true);

        grid.add(new Label("Collection:"), 0, 0);
        grid.add(colCombo, 1, 0);
        grid.add(new Label("Format:"), 0, 1);
        grid.add(formatCombo, 1, 1);
        grid.add(new Label("Output file:"), 0, 2);
        HBox fileRow = new HBox(8, browseBtn, fileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(fileRow, 1, 2);
        grid.add(includeEnvsBox, 1, 3);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(420);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK || outputPath[0] == null) return;
            try {
                new ImportExportService().exportCollection(
                        storage, colCombo.getValue(), outputPath[0],
                        formatCombo.getValue(), includeEnvsBox.isSelected());
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Complete");
                alert.setHeaderText(null);
                alert.setContentText("Exported '" + colCombo.getValue() + "' to:\n" + outputPath[0].getFileName());
                alert.showAndWait();
            } catch (IOException e) {
                showError("Export failed: " + e.getMessage());
            }
        });
    }

    private ImportResult autoDetect(Path file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(file.toFile());
        InsomniaImporter insomnia = new InsomniaImporter();
        if (insomnia.isInsomnia(root)) return insomnia.importFile(file, null);
        PostmanImporter postman = new PostmanImporter();
        if (postman.isCollection(root) || postman.isEnvironment(root)) return postman.importFile(file, null);
        return new NativeImporter().importFile(file, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tree context menu
    // ─────────────────────────────────────────────────────────────────────────

    private void setupTreeContextMenu() {
        collectionTree.setCellFactory(tv -> {
            TreeCell<TreeNodeData> cell = new TreeCell<>() {
                @Override
                protected void updateItem(TreeNodeData item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                        return;
                    }
                    setText(item.displayName());
                    TreeItem<TreeNodeData> ti = getTreeItem();
                    setGraphic(ti != null ? ti.getGraphic() : null);
                    boolean isDirtyCell = (item instanceof TreeNodeData.Request rd)
                            && getTabControllers().stream()
                                    .anyMatch(c -> c.isRequestDirty(rd.collectionName(), rd.folderPath(), rd.name()));
                    setStyle(isDirtyCell ? "-fx-text-fill: #e05a00; -fx-font-style: italic;" : "");
                }
            };

            cell.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) return;
                TreeItem<TreeNodeData> treeItem = cell.getTreeItem();
                if (treeItem == null) return;
                if (treeItem.getValue() instanceof TreeNodeData.Request rd) {
                    openRequestInNewTab(rd.collectionName(), rd.folderPath(), rd.name());
                }
            });

            cell.setOnContextMenuRequested(event -> {
                TreeItem<TreeNodeData> treeItem = cell.getTreeItem();
                if (treeItem == null) return;
                TreeNodeData data = treeItem.getValue();
                ContextMenu menu = new ContextMenu();

                if (data instanceof TreeNodeData.Collection col) {
                    MenuItem newReq = new MenuItem("New Request");
                    newReq.setOnAction(e -> {
                        RequestTabController t = currentTabController();
                        if (t != null) t.newRequest();
                    });
                    MenuItem newFolder = new MenuItem("New Folder");
                    newFolder.setOnAction(e -> onNewFolder(col.name(), List.of()));
                    MenuItem renameCol = new MenuItem("Rename…");
                    renameCol.setOnAction(e -> onRenameCollection(col.name()));
                    MenuItem deleteCol = new MenuItem("Delete Collection");
                    deleteCol.setOnAction(e -> onDeleteCollection(col.name()));
                    menu.getItems().addAll(newReq, newFolder, renameCol, deleteCol);

                } else if (data instanceof TreeNodeData.Folder folder) {
                    MenuItem newReq = new MenuItem("New Request");
                    newReq.setOnAction(e -> {
                        RequestTabController t = currentTabController();
                        if (t != null) t.newRequest();
                    });
                    MenuItem newFolder = new MenuItem("New Folder");
                    newFolder.setOnAction(e -> onNewFolder(folder.collectionName(), folder.path()));
                    MenuItem renameFolder = new MenuItem("Rename…");
                    renameFolder.setOnAction(e -> onRenameFolder(folder.collectionName(), folder.path()));
                    MenuItem deleteFolder = new MenuItem("Delete Folder");
                    deleteFolder.setOnAction(e -> onDeleteFolder(folder.collectionName(), folder.path()));
                    menu.getItems().addAll(newReq, newFolder, renameFolder, deleteFolder);

                } else if (data instanceof TreeNodeData.Request req) {
                    MenuItem renameReq = new MenuItem("Rename…");
                    renameReq.setOnAction(e -> onRenameRequest(req.collectionName(), req.folderPath(), req.name()));
                    MenuItem moveReq = new MenuItem("Move to…");
                    moveReq.setOnAction(e -> onMoveRequest(req.collectionName(), req.folderPath(), req.name()));
                    MenuItem deleteReq = new MenuItem("Delete Request");
                    deleteReq.setOnAction(e -> onDeleteRequest(req.collectionName(), req.folderPath(), req.name()));
                    menu.getItems().addAll(renameReq, moveReq, deleteReq);
                }

                if (!menu.getItems().isEmpty()) {
                    menu.show(cell, event.getScreenX(), event.getScreenY());
                }
                event.consume();
            });

            cell.setOnDragDetected(event -> {
                TreeItem<TreeNodeData> item = cell.getTreeItem();
                if (item == null || item.getValue() instanceof TreeNodeData.Collection) return;
                dragSourceItem = item;
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(item.getValue().displayName());
                db.setContent(content);
                event.consume();
            });

            cell.setOnDragOver(event -> {
                TreeItem<TreeNodeData> target = cell.getTreeItem();
                if (dragSourceItem == null || target == null || target == dragSourceItem) return;
                boolean isSiblingReorder = target.getParent() == dragSourceItem.getParent()
                        && target.getValue().getClass() == dragSourceItem.getValue().getClass();
                if (isSiblingReorder) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    cell.setStyle(cell.getStyle() + "-fx-border-color: #4a9eff; -fx-border-width: 0 0 2 0;");
                } else if (isValidDropContainer(target, dragSourceItem)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    cell.setStyle(cell.getStyle() + "-fx-border-color: #4a9eff; -fx-border-width: 1;");
                }
                event.consume();
            });

            cell.setOnDragExited(event -> {
                String style = cell.getStyle()
                        .replace("-fx-border-color: #4a9eff; -fx-border-width: 0 0 2 0;", "")
                        .replace("-fx-border-color: #4a9eff; -fx-border-width: 1;", "");
                cell.setStyle(style);
            });

            cell.setOnDragDropped(event -> {
                TreeItem<TreeNodeData> target = cell.getTreeItem();
                if (dragSourceItem == null || target == null || target == dragSourceItem) return;
                boolean isSiblingReorder = target.getParent() == dragSourceItem.getParent()
                        && target.getValue().getClass() == dragSourceItem.getValue().getClass();
                if (isSiblingReorder) {
                    performReorder(dragSourceItem, target);
                    event.setDropCompleted(true);
                } else if (isValidDropContainer(target, dragSourceItem)) {
                    performDragMove(dragSourceItem, target);
                    event.setDropCompleted(true);
                }
                event.consume();
            });

            cell.setOnDragDone(event -> {
                dragSourceItem = null;
                event.consume();
            });

            return cell;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection CRUD
    // ─────────────────────────────────────────────────────────────────────────

    private void onDeleteCollection(String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Collection");
        alert.setHeaderText("Delete \"" + name + "\"?");
        alert.setContentText("All saved requests in this collection will be removed.");
        alert.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                storage.deleteCollection(name);
                for (RequestTabController ctrl : getTabControllers()) {
                    if (name.equals(ctrl.getCurrentCollection())) ctrl.newRequest();
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to delete collection: " + e.getMessage());
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
                if (existing.contains(name)) {
                    showError("A collection named \"" + name + "\" already exists.");
                    return;
                }
                RequestCollection col = storage.loadCollection(oldName);
                col.setName(name);
                storage.saveCollection(col);
                storage.deleteCollection(oldName);
                for (RequestTabController ctrl : getTabControllers()) {
                    if (oldName.equals(ctrl.getCurrentCollection())) ctrl.notifyCollectionRenamed(name);
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to rename collection: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Folder CRUD
    // ─────────────────────────────────────────────────────────────────────────

    private void onNewFolder(String collectionName, List<String> parentPath) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Folder");
        dlg.setHeaderText(null);
        dlg.setContentText("Folder name:");
        dlg.showAndWait().ifPresent(raw -> {
            String name = raw.trim();
            if (name.isBlank()) return;
            try {
                RequestCollection col = storage.loadCollection(collectionName);
                StorageService.addFolder(col, parentPath, name);
                storage.saveCollection(col);
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to create folder: " + e.getMessage());
            }
        });
    }

    private void onRenameFolder(String collectionName, List<String> path) {
        String oldName = path.get(path.size() - 1);
        TextInputDialog dlg = new TextInputDialog(oldName);
        dlg.setTitle("Rename Folder");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(raw -> {
            String name = raw.trim();
            if (name.isBlank() || name.equals(oldName)) return;
            try {
                RequestCollection col = storage.loadCollection(collectionName);
                StorageService.renameFolder(col, path, name);
                storage.saveCollection(col);
                // Update tabs that were showing requests inside this folder
                List<String> newPath = new ArrayList<>(path.subList(0, path.size() - 1));
                newPath.add(name);
                for (RequestTabController ctrl : getTabControllers()) {
                    ctrl.notifyFolderRenamed(collectionName, path, List.copyOf(newPath));
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to rename folder: " + e.getMessage());
            }
        });
    }

    private void onDeleteFolder(String collectionName, List<String> path) {
        String folderName = path.get(path.size() - 1);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Folder");
        alert.setHeaderText("Delete folder \"" + folderName + "\"?");
        alert.setContentText("All requests and subfolders inside will be removed.");
        alert.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                RequestCollection col = storage.loadCollection(collectionName);
                StorageService.deleteFolder(col, path);
                storage.saveCollection(col);
                for (RequestTabController ctrl : getTabControllers()) {
                    List<String> ctrlPath = ctrl.getCurrentFolderPath();
                    if (collectionName.equals(ctrl.getCurrentCollection())
                            && ctrlPath != null && pathStartsWith(ctrlPath, path)) {
                        ctrl.newRequest();
                    }
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to delete folder: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request CRUD
    // ─────────────────────────────────────────────────────────────────────────

    private void onDeleteRequest(String collectionName, List<String> folderPath, String requestName) {
        try {
            RequestCollection col = storage.loadCollection(collectionName);
            StorageService.deleteRequest(col, folderPath, requestName);
            storage.saveCollection(col);
            for (RequestTabController ctrl : getTabControllers()) {
                if (ctrl.isShowing(collectionName, folderPath, requestName)) ctrl.newRequest();
            }
            refreshCollections();
        } catch (IOException e) {
            showError("Failed to delete request: " + e.getMessage());
        }
    }

    private void onMoveRequest(String srcCollection, List<String> srcFolderPath, String requestName) {
        List<String> allCollections;
        try {
            allCollections = storage.listCollectionNames();
        } catch (IOException e) {
            showError("Failed to list collections: " + e.getMessage());
            return;
        }

        Callback<ListView<List<String>>, ListCell<List<String>>> folderCellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(List<String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (item.isEmpty() ? "(top level)" : String.join(" / ", item)));
            }
        };

        ComboBox<String> colCombo = new ComboBox<>();
        colCombo.getItems().addAll(allCollections);
        colCombo.setValue(srcCollection);
        colCombo.setPrefWidth(260);

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
                if (colName.equals(srcCollection) && paths.contains(srcFolderPath)) {
                    folderCombo.setValue(srcFolderPath);
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

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 16, 6, 16));
        grid.add(new Label("Collection:"), 0, 0);
        grid.add(colCombo, 1, 0);
        grid.add(new Label("Folder:"), 0, 1);
        grid.add(folderCombo, 1, 1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Move \"" + requestName + "\"");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(400);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String destCollection = colCombo.getValue();
            List<String> destFolderPath = folderCombo.getValue() != null ? folderCombo.getValue() : List.of();

            if (destCollection.equals(srcCollection) && destFolderPath.equals(srcFolderPath)) return;

            try {
                // Remove from source
                RequestCollection srcCol = storage.loadCollection(srcCollection);
                StorageService.findRequest(srcCol, srcFolderPath, requestName).ifPresent(req -> {
                    try {
                        StorageService.deleteRequest(srcCol, srcFolderPath, requestName);
                        storage.saveCollection(srcCol);

                        // Add to destination (may be same collection object — reload to be safe)
                        RequestCollection destCol = destCollection.equals(srcCollection)
                                ? storage.loadCollection(destCollection)
                                : storage.loadCollection(destCollection);
                        StorageService.ensureFolderPath(destCol, destFolderPath);
                        req.setName(requestName);
                        StorageService.upsertRequest(destCol, destFolderPath, req);
                        storage.saveCollection(destCol);

                        for (RequestTabController ctrl : getTabControllers()) {
                            if (ctrl.isShowing(srcCollection, srcFolderPath, requestName)) {
                                ctrl.notifyRequestMoved(destCollection, destFolderPath);
                            }
                        }
                        refreshCollections();
                    } catch (IOException ex) {
                        showError("Failed to move request: " + ex.getMessage());
                    }
                });
            } catch (IOException e) {
                showError("Failed to move request: " + e.getMessage());
            }
        });
    }

    private void onRenameRequest(String collectionName, List<String> folderPath, String oldName) {
        TextInputDialog dlg = new TextInputDialog(oldName);
        dlg.setTitle("Rename Request");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(raw -> {
            final String name = raw.trim();
            if (name.isBlank() || name.equals(oldName)) return;
            try {
                RequestCollection col = storage.loadCollection(collectionName);
                if (StorageService.findRequest(col, folderPath, name).isPresent()) {
                    showError("A request named \"" + name + "\" already exists.");
                    return;
                }
                StorageService.renameRequest(col, folderPath, oldName, name);
                storage.saveCollection(col);
                for (RequestTabController ctrl : getTabControllers()) {
                    if (ctrl.isShowing(collectionName, folderPath, oldName)) {
                        ctrl.notifyRequestRenamed(name);
                    }
                }
                refreshCollections();
            } catch (IOException e) {
                showError("Failed to rename request: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onSettings() {
        AppConfig config;
        try {
            config = storage.loadConfig();
        } catch (IOException e) {
            showError("Failed to load config: " + e.getMessage());
            return;
        }

        String currentFamily = config.getEditorFontFamily() != null ? config.getEditorFontFamily() : "Consolas";
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
                getClass().getResource("/gottschlinger/howlman/gui/style.css").toExternalForm());

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
        for (RequestTabController ctrl : getTabControllers()) ctrl.applyEditorFont(config);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Environment CRUD
    // ─────────────────────────────────────────────────────────────────────────

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
        keyCol.setCellFactory(envCellFactory(HeaderRow::setKey));

        TableColumn<HeaderRow, String> valCol = new TableColumn<>("Value");
        valCol.setPrefWidth(260);
        valCol.setCellValueFactory(c -> c.getValue().valueProperty());
        valCol.setCellFactory(envCellFactory(HeaderRow::setValue));

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
                getClass().getResource("/gottschlinger/howlman/gui/style.css").toExternalForm());

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            Map<String, String> vars = new LinkedHashMap<>();
            for (HeaderRow row : varRows) {
                if (!row.getKey().isBlank()) vars.put(row.getKey().trim(), row.getValue());
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
                if (existing.contains(name)) {
                    showError("An environment named \"" + name + "\" already exists.");
                    return;
                }
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

    private void performReorder(TreeItem<TreeNodeData> source, TreeItem<TreeNodeData> target) {
        TreeNodeData srcData = source.getValue();
        TreeNodeData tgtData = target.getValue();
        try {
            if (srcData instanceof TreeNodeData.Request srcReq) {
                RequestCollection col = storage.loadCollection(srcReq.collectionName());
                List<SavedRequest> list = requestsAt(col, srcReq.folderPath());
                if (list == null) return;
                int srcIdx = indexByName(list, srcReq.name(), SavedRequest::getName);
                int tgtIdx = indexByName(list, ((TreeNodeData.Request) tgtData).name(), SavedRequest::getName);
                if (srcIdx < 0 || tgtIdx < 0 || srcIdx == tgtIdx) return;
                list.add(tgtIdx, list.remove(srcIdx));
                storage.saveCollection(col);
            } else if (srcData instanceof TreeNodeData.Folder srcFolder) {
                RequestCollection col = storage.loadCollection(srcFolder.collectionName());
                List<String> parentPath = srcFolder.path().subList(0, srcFolder.path().size() - 1);
                List<RequestFolder> list = foldersAt(col, parentPath);
                if (list == null) return;
                int srcIdx = indexByName(list, srcFolder.name(), RequestFolder::getName);
                int tgtIdx = indexByName(list, ((TreeNodeData.Folder) tgtData).name(), RequestFolder::getName);
                if (srcIdx < 0 || tgtIdx < 0 || srcIdx == tgtIdx) return;
                list.add(tgtIdx, list.remove(srcIdx));
                storage.saveCollection(col);
            }
            refreshCollections();
        } catch (IOException e) {
            showError("Failed to reorder: " + e.getMessage());
        }
    }

    private boolean isValidDropContainer(TreeItem<TreeNodeData> target, TreeItem<TreeNodeData> source) {
        TreeNodeData tgtData = target.getValue();
        if (!(tgtData instanceof TreeNodeData.Collection || tgtData instanceof TreeNodeData.Folder)) return false;
        // Can't drop onto the source's current parent
        if (target == source.getParent()) return false;
        // Can't drop a folder into itself or any of its descendants
        if (source.getValue() instanceof TreeNodeData.Folder srcFolder
                && tgtData instanceof TreeNodeData.Folder tgtFolder) {
            if (pathStartsWith(tgtFolder.path(), srcFolder.path())) return false;
        }
        return true;
    }

    private void performDragMove(TreeItem<TreeNodeData> source, TreeItem<TreeNodeData> target) {
        TreeNodeData srcData = source.getValue();
        TreeNodeData tgtData = target.getValue();

        String destCollection = (tgtData instanceof TreeNodeData.Collection c) ? c.name()
                : ((TreeNodeData.Folder) tgtData).collectionName();
        List<String> destFolderPath = (tgtData instanceof TreeNodeData.Folder f) ? f.path() : List.of();

        try {
            if (srcData instanceof TreeNodeData.Request srcReq) {
                String srcCollection = srcReq.collectionName();
                List<String> srcFolderPath = srcReq.folderPath();
                String reqName = srcReq.name();

                RequestCollection srcCol = storage.loadCollection(srcCollection);
                StorageService.findRequest(srcCol, srcFolderPath, reqName).ifPresent(req -> {
                    try {
                        StorageService.deleteRequest(srcCol, srcFolderPath, reqName);
                        storage.saveCollection(srcCol);

                        RequestCollection destCol = storage.loadCollection(destCollection);
                        StorageService.ensureFolderPath(destCol, destFolderPath);
                        StorageService.upsertRequest(destCol, destFolderPath, req);
                        storage.saveCollection(destCol);

                        for (RequestTabController ctrl : getTabControllers()) {
                            if (ctrl.isShowing(srcCollection, srcFolderPath, reqName)) {
                                ctrl.notifyRequestMoved(destCollection, destFolderPath);
                            }
                        }
                        refreshCollections();
                    } catch (IOException ex) {
                        showError("Failed to move request: " + ex.getMessage());
                    }
                });

            } else if (srcData instanceof TreeNodeData.Folder srcFolder) {
                String srcCollection = srcFolder.collectionName();
                List<String> srcPath = srcFolder.path();

                RequestCollection srcCol = storage.loadCollection(srcCollection);
                RequestFolder folderObj = StorageService.resolveFolder(srcCol, srcPath);
                if (folderObj == null) return;

                StorageService.deleteFolder(srcCol, srcPath);
                storage.saveCollection(srcCol);

                RequestCollection destCol = storage.loadCollection(destCollection);
                StorageService.ensureFolderPath(destCol, destFolderPath);
                List<RequestFolder> destList = destFolderPath.isEmpty()
                        ? destCol.getFolders()
                        : StorageService.resolveFolder(destCol, destFolderPath).getFolders();
                destList.add(folderObj);
                storage.saveCollection(destCol);

                // Reset any open tabs that were showing requests inside the moved folder
                for (RequestTabController ctrl : getTabControllers()) {
                    List<String> ctrlPath = ctrl.getCurrentFolderPath();
                    if (srcCollection.equals(ctrl.getCurrentCollection())
                            && ctrlPath != null && pathStartsWith(ctrlPath, srcPath)) {
                        ctrl.newRequest();
                    }
                }
                refreshCollections();
            }
        } catch (IOException e) {
            showError("Failed to move: " + e.getMessage());
        }
    }

    private static List<SavedRequest> requestsAt(RequestCollection col, List<String> folderPath) {
        if (folderPath == null || folderPath.isEmpty()) return col.getRequests();
        RequestFolder folder = StorageService.resolveFolder(col, folderPath);
        return folder != null ? folder.getRequests() : null;
    }

    private static List<RequestFolder> foldersAt(RequestCollection col, List<String> parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return col.getFolders();
        RequestFolder folder = StorageService.resolveFolder(col, parentPath);
        return folder != null ? folder.getFolders() : null;
    }

    private static <T> int indexByName(List<T> list, String name, java.util.function.Function<T, String> getName) {
        for (int i = 0; i < list.size(); i++) {
            if (name.equals(getName.apply(list.get(i)))) return i;
        }
        return -1;
    }

    @FXML
    private void onAbout() {
        ImageView icon = new ImageView(
                new Image(getClass().getResourceAsStream("/gottschlinger/howlman/gui/icon.png")));
        icon.setFitWidth(96);
        icon.setFitHeight(96);
        icon.setPreserveRatio(true);

        Label name    = new Label("HowlMan");
        name.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        String versionText = "unknown";
        try (var in = getClass().getResourceAsStream("/version.properties")) {
            if (in != null) {
                var props = new java.util.Properties();
                props.load(in);
                versionText = props.getProperty("version", "unknown");
            }
        } catch (IOException ignored) {}
        Label version = new Label("Version " + versionText);
        version.setStyle("-fx-text-fill: #8b949e;");
        Label desc    = new Label("A lightweight HTTP request client.");
        desc.setStyle("-fx-text-fill: #8b949e;");
        Label author  = new Label("By Mark Gottschling");
        author.setStyle("-fx-text-fill: #8b949e;");

        Label license = new Label("Freeware for personal use. Donations are welcome!");
        license.setStyle("-fx-font-size: 11px;");
        Label donateNote = new Label("Donate via:");
        donateNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b949e;");

        javafx.scene.control.Hyperlink kofiLink = new javafx.scene.control.Hyperlink("Ko-fi");
        kofiLink.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        kofiLink.setOnAction(e -> openUrl("https://ko-fi.com/gottschlinger"));

        javafx.scene.control.Hyperlink bmacLink = new javafx.scene.control.Hyperlink("Buy Me a Coffee");
        bmacLink.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        bmacLink.setOnAction(e -> openUrl("https://www.buymeacoffee.com/gottschlinger"));

        HBox donateRow = new HBox(4, donateNote, kofiLink, new Label("·"), bmacLink);
        donateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Hyperlink githubLink = new javafx.scene.control.Hyperlink("github.com/gottschlinger");
        githubLink.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        githubLink.setOnAction(e -> openUrl("https://github.com/gottschlinger"));

        HBox contactRow = new HBox(4, new Label("Contact:"), githubLink);
        contactRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox text = new VBox(4, name, version, desc, author);
        text.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox iconRow = new HBox(16, icon, text);
        iconRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-padding: 4 0 4 0;");

        VBox content = new VBox(10, iconRow, sep, license, donateRow, contactRow);
        content.setPadding(new Insets(20, 24, 12, 24));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("About HowlMan");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/gottschlinger/howlman/gui/style.css").toExternalForm());
        dialog.showAndWait();
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            showError("Could not open browser: " + e.getMessage());
        }
    }

    private static boolean pathStartsWith(List<String> path, List<String> prefix) {
        if (path.size() < prefix.size()) return false;
        return path.subList(0, prefix.size()).equals(prefix);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Simple text-commit cell factory for the environment editor (no dirty tracking). */
    private Callback<TableColumn<HeaderRow, String>, TableCell<HeaderRow, String>>
            envCellFactory(BiConsumer<HeaderRow, String> setter) {
        return col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.focusedProperty().addListener((obs, was, focused) -> { if (!focused) commit(); });
                tf.setOnAction(e -> commit());
            }

            private void commit() {
                HeaderRow row = getTableRow() != null ? (HeaderRow) getTableRow().getItem() : null;
                if (row != null) setter.accept(row, tf.getText());
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
