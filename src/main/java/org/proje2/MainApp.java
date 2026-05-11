package org.proje2;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.proje2.model.Table;
import org.proje2.parser.JsonParser;
import org.proje2.converter.SqlGenerator;
import org.proje2.database.DatabaseManager;

import java.io.File;
import java.sql.*;
import java.util.List;

/**
 * Main JavaFX GUI Application.
 * Features: JSON File Selection, Tree View visualization, Data Table display, and DB Reset.
 */
public class MainApp extends Application {

    private TreeView<String> jsonTreeView = new TreeView<>();
    private TableView<ObservableList<Object>> dbTableView = new TableView<>();
    private ListView<String> tablesListView = new ListView<>();
    private List<Table> currentTables;
    private final DatabaseManager dbManager = new DatabaseManager();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("NoSQL to SQL Converter - ProLab II");

        // --- TOP PANEL (Controls) ---
        Button btnLoad = new Button("Select JSON File");
        Button btnReset = new Button("Reset Database");
        HBox topPanel = new HBox(10, btnLoad, btnReset);
        topPanel.setPadding(new Insets(10));

        // --- LEFT PANEL (JSON Structure) ---
        VBox leftPanel = new VBox(5, new Label("JSON Hierarchy"), jsonTreeView);
        leftPanel.setPadding(new Insets(10));
        jsonTreeView.setPrefWidth(250);

        // --- RIGHT PANEL (Table List) ---
        VBox rightPanel = new VBox(5, new Label("Generated Tables"), tablesListView);
        rightPanel.setPadding(new Insets(10));
        tablesListView.setPrefWidth(150);

        // --- CENTER PANEL (Data Grid) ---
        VBox centerPanel = new VBox(5, new Label("Table Data Content"), dbTableView);
        centerPanel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topPanel);
        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setRight(rightPanel);

        // --- EVENTS ---
        btnLoad.setOnAction(e -> loadJsonFile(primaryStage));
        btnReset.setOnAction(e -> resetDatabase());
        tablesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) showTableData(newVal);
        });

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Loads JSON from file, builds the tree view, and initiates conversion in background.
     */
    private void loadJsonFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(file);

                // 1. Populate TreeView
                TreeItem<String> rootItem = new TreeItem<>("Root");
                buildTree(rootNode, rootItem);
                jsonTreeView.setRoot(rootItem);
                rootItem.setExpanded(true);

                // 2. Parse JSON to Tables
                JsonParser parser = new JsonParser();
                currentTables = parser.parse(rootNode);

                // 3. Export to Database in BACKGROUND to keep UI responsive
                javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        SqlGenerator sqlGenerator = new SqlGenerator();
                        sqlGenerator.exportToDatabase(currentTables);
                        return null;
                    }
                };

                task.setOnSucceeded(workerStateEvent -> {
                    // Update table list on UI thread
                    tablesListView.getItems().clear();
                    for (Table t : currentTables) {
                        tablesListView.getItems().add(t.getName());
                    }
                    showAlert("Success", "JSON converted and exported to database successfully.");
                });

                task.setOnFailed(workerStateEvent -> {
                    showAlert("Error", "Database export failed: " + task.getException().getMessage());
                });

                new Thread(task).start();

            } catch (Exception ex) {
                showAlert("Error", "File processing error: " + ex.getMessage());
            }
        }
    }

    /**
     * Recursively builds the TreeView representation of JSON.
     */
    private void buildTree(JsonNode node, TreeItem<String> parent) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                TreeItem<String> child = new TreeItem<>(entry.getKey());
                parent.getChildren().add(child);
                buildTree(entry.getValue(), child);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                TreeItem<String> child = new TreeItem<>("[" + i + "]");
                parent.getChildren().add(child);
                buildTree(node.get(i), child);
            }
        } else {
            parent.setValue(parent.getValue() + " : " + node.asText());
        }
    }

    /**
     * Fetches and displays records for the selected table in the TableView.
     */
    private void showTableData(String tableName) {
        dbTableView.getColumns().clear();
        dbTableView.getItems().clear();

        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Create dynamic columns
            for (int i = 1; i <= columnCount; i++) {
                final int j = i - 1;
                TableColumn<ObservableList<Object>, Object> col = new TableColumn<>(metaData.getColumnName(i));
                col.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(j)));
                dbTableView.getColumns().add(col);
            }

            // Populate rows
            while (rs.next()) {
                ObservableList<Object> row = FXCollections.observableArrayList();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                dbTableView.getItems().add(row);
            }

        } catch (SQLException ex) {
            showAlert("Error", "Data reading error: " + ex.getMessage());
        }
    }

    /**
     * Drops all user-created tables in the database.
     */
    private void resetDatabase() {
        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement()) {
            
            List<String> tableNames = new java.util.ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Skip SQLite system tables
                    if (!tableName.startsWith("sqlite_")) {
                        tableNames.add(tableName);
                    }
                }
            }

            for (String tableName : tableNames) {
                stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            }
            
            tablesListView.getItems().clear();
            dbTableView.getColumns().clear();
            dbTableView.getItems().clear();
            jsonTreeView.setRoot(null);
            
            showAlert("Reset", "All database tables have been dropped.");
        } catch (SQLException ex) {
            showAlert("Error", "Reset error: " + ex.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
