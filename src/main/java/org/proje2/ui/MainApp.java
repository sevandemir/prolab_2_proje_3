package org.proje2.ui;

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
 * Ana JavaFX GUI (Grafiksel Kullanıcı Arayüzü) Uygulaması.
 * Özellikler: JSON Dosyası Seçimi, Ağaç Görünümü (Tree View) görselleştirmesi, 
 * Veri Tablosu (Data Table) gösterimi ve Veritabanı Sıfırlama işlemleri.
 */
public class MainApp extends Application {

    // JSON dosyasının hiyerarşik yapısını gösterecek Ağaç Görünümü
    private TreeView<String> jsonTreeView = new TreeView<>();
    // Veritabanındaki tablo verilerini gösterecek Tablo Görünümü
    private TableView<ObservableList<Object>> dbTableView = new TableView<>();
    // Oluşturulan veritabanı tablolarının isimlerini listeleyecek Liste Görünümü
    private ListView<String> tablesListView = new ListView<>();
    // Mevcut (çözümlenmiş) tabloların listesi
    private List<Table> currentTables;
    // Veritabanı bağlantılarını yönetecek nesne
    private final DatabaseManager dbManager = new DatabaseManager();

    /**
     * JavaFX uygulamasının başlatıldığı ve arayüzün oluşturulduğu metot.
     * 
     * @param primaryStage Uygulamanın ana penceresi
     */
    @Override
    public void start(Stage primaryStage) {
        // Pencere başlığını ayarla
        primaryStage.setTitle("NoSQL'den SQL'e Dönüştürücü - ProLab II");

        // --- ÜST PANEL (Kontroller) ---
        Button btnLoad = new Button("JSON Dosyası Seç"); // Dosya yükleme butonu
        Button btnReset = new Button("Veritabanını Sıfırla"); // Veritabanı sıfırlama butonu
        HBox topPanel = new HBox(10, btnLoad, btnReset); // Butonları yatayda yerleştir
        topPanel.setPadding(new Insets(10)); // Panele boşluk ekle

        // --- SOL PANEL (JSON Yapısı) ---
        VBox leftPanel = new VBox(5, new Label("JSON Hiyerarşisi"), jsonTreeView); // Ağaç görünümünü dikey yerleştir
        leftPanel.setPadding(new Insets(10));
        jsonTreeView.setPrefWidth(250); // Ağaç görünümünün genişliğini belirle

        // --- SAĞ PANEL (Tablo Listesi) ---
        VBox rightPanel = new VBox(5, new Label("Üretilen Tablolar"), tablesListView); // Tablo listesini dikey yerleştir
        rightPanel.setPadding(new Insets(10));
        tablesListView.setPrefWidth(150); // Liste görünümünün genişliğini belirle

        // --- ORTA PANEL (Veri Izgarası & SQL Sorgusu) ---
        // Üst kısım: Tablo içeriğini göster
        VBox centerTopPanel = new VBox(5, new Label("Tablo Veri İçeriği / Sorgu Sonuçları"), dbTableView);
        centerTopPanel.setPadding(new Insets(10));
        // TableView alanının boyutunu pencere ile birlikte büyüyecek şekilde ayarla
        VBox.setVgrow(dbTableView, javafx.scene.layout.Priority.ALWAYS);

        // Alt kısım: Özel SQL sorgusu çalıştırmak için metin alanı
        TextArea sqlInputArea = new TextArea();
        sqlInputArea.setPromptText("SQL sorgunuzu buraya girin (ör. SELECT * FROM main_table)");
        sqlInputArea.setPrefRowCount(4); // Metin alanının varsayılan satır sayısı
        Button btnExecuteSql = new Button("SQL Çalıştır"); // Sorgu çalıştırma butonu
        HBox sqlControls = new HBox(10, btnExecuteSql);
        VBox sqlPanel = new VBox(5, new Label("Özel SQL Sorgusu"), sqlInputArea, sqlControls);
        sqlPanel.setPadding(new Insets(10));
        
        // Veri tablosu ile SQL sorgu alanını birbirinden ayırmak için SplitPane (bölmeli panel) kullan
        SplitPane centerSplit = new SplitPane(centerTopPanel, sqlPanel);
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL); // Bölmeyi dikey ayarla
        centerSplit.setDividerPositions(0.7); // Bölme çizgisinin konumunu belirle (%70 üst, %30 alt)

        // Ana düzeni belirleyen BorderPane
        BorderPane root = new BorderPane();
        root.setTop(topPanel);       // Üste kontrolleri ekle
        root.setLeft(leftPanel);     // Sola JSON ağacını ekle
        root.setCenter(centerSplit); // Ortaya verileri ve SQL sorgu panelini ekle
        root.setRight(rightPanel);   // Sağa tablo listesini ekle

        // --- OLAYLAR (EVENTS) ---
        // Yükleme butonuna tıklanınca loadJsonFile metodunu çağır
        btnLoad.setOnAction(e -> loadJsonFile(primaryStage));
        // Sıfırlama butonuna tıklanınca resetDatabase metodunu çağır
        btnReset.setOnAction(e -> resetDatabase());
        // SQL Çalıştır butonuna tıklanınca executeCustomQuery metodunu çağır
        btnExecuteSql.setOnAction(e -> executeCustomQuery(sqlInputArea.getText()));
        // Tablo listesinden bir seçim yapıldığında (seçili öğe değiştiğinde) tablo verisini göster
        tablesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) showTableData(newVal);
        });

        // 1000x600 boyutlarında yeni bir sahne (Scene) oluştur ve ana pencereye yerleştir
        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show(); // Pencereyi görünür yap
    }

    /**
     * Dosyadan JSON yükler, ağaç görünümünü oluşturur ve arka planda dönüştürme işlemini başlatır.
     * 
     * @param stage Dosya seçici iletişim kutusu için referans ana pencere
     */
    private void loadJsonFile(Stage stage) {
        // Dosya seçici nesnesi oluştur
        FileChooser fileChooser = new FileChooser();
        // Sadece .json uzantılı dosyaları kabul edecek şekilde filtre ekle
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Dosyaları", "*.json"));
        File file = fileChooser.showOpenDialog(stage); // Dosya seçici iletişim kutusunu göster

        // Kullanıcı bir dosya seçtiyse
        if (file != null) {
            try {
                // Jackson kütüphanesini kullanarak JSON dosyasını oku ve ağaç yapısına çevir
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(file);

                // 1. Ağaç Görünümünü (TreeView) Doldur
                TreeItem<String> rootItem = new TreeItem<>("Kök (Root)");
                buildTree(rootNode, rootItem); // Öz yinelemeli olarak ağacı inşa et
                jsonTreeView.setRoot(rootItem); // Oluşturulan yapıyı TreeView'a ata
                rootItem.setExpanded(true); // Kök düğümünü başlangıçta açık şekilde göster

                // 2. JSON'ı ayrıştırarak Tablolara dönüştür
                JsonParser parser = new JsonParser();
                currentTables = parser.parse(rootNode);

                // 3. Arayüzün donmaması için veritabanına aktarma işlemini ARKA PLANDA yap
                javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        SqlGenerator sqlGenerator = new SqlGenerator();
                        // Dönüştürülen tabloları veritabanına aktar
                        sqlGenerator.exportToDatabase(currentTables);
                        return null;
                    }
                };

                // Görev (Task) başarıyla tamamlandığında çalışacak kod bloğu
                task.setOnSucceeded(workerStateEvent -> {
                    // Kullanıcı arayüzünü (UI iş parçacığı) güncelleyerek tablo listesini doldur
                    tablesListView.getItems().clear();
                    for (Table t : currentTables) {
                        tablesListView.getItems().add(t.getName());
                    }
                    // Başarı mesajı göster
                    showAlert("Başarılı", "JSON başarıyla dönüştürüldü ve veritabanına aktarıldı.");
                });

                // Görev sırasında hata oluşursa çalışacak kod bloğu
                task.setOnFailed(workerStateEvent -> {
                    showAlert("Hata", "Veritabanına aktarım başarısız oldu: " + task.getException().getMessage());
                });

                // Arka plan görevini yeni bir iş parçacığında (Thread) başlat
                new Thread(task).start();

            } catch (Exception ex) {
                // Dosya işleme sırasında genel bir hata olursa mesajı göster
                showAlert("Hata", "Dosya işleme hatası: " + ex.getMessage());
            }
        }
    }

    /**
     * JSON verisinin ağaç (Tree) temsilini öz yinelemeli olarak (recursive) oluşturur.
     * 
     * @param node İşlenen mevcut JSON düğümü
     * @param parent Ağaca eklenecek olan üst (parent) TreeItem
     */
    private void buildTree(JsonNode node, TreeItem<String> parent) {
        if (node.isObject()) {
            // Düğüm bir nesne ise, tüm anahtar-değer çiftleri üzerinden dön
            node.fields().forEachRemaining(entry -> {
                TreeItem<String> child = new TreeItem<>(entry.getKey());
                parent.getChildren().add(child); // Ağaca çocuk düğümü ekle
                buildTree(entry.getValue(), child); // Alt düğümü (nesne) işlemek için kendini çağır
            });
        } else if (node.isArray()) {
            // Düğüm bir dizi ise, elemanları dizinleriyle (index) birlikte döngüye sok
            for (int i = 0; i < node.size(); i++) {
                TreeItem<String> child = new TreeItem<>("[" + i + "]");
                parent.getChildren().add(child); // Dizi indeksini ağaca ekle
                buildTree(node.get(i), child); // Dizi elemanını işlemek için kendini çağır
            }
        } else {
            // Düğüm basit bir değerse (String, Sayı vb.), bunu anahtarın yanına ": değer" şeklinde yaz
            parent.setValue(parent.getValue() + " : " + node.asText());
        }
    }

    /**
     * Seçili olan tablo için kayıtları veritabanından getirir ve TableView'da gösterir.
     * 
     * @param tableName Görüntülenecek tablonun adı
     */
    private void showTableData(String tableName) {
        // Tablonun eski sütunlarını ve verilerini temizle
        dbTableView.getColumns().clear();
        dbTableView.getItems().clear();

        // Veritabanına bağlan ve sorguyu çalıştır (try-with-resources kullanılarak otomatik kapatılır)
        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            // Sorgudan dönen verilerin meta verilerini (sütun sayısı, adları vb.) al
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Dinamik olarak Tablo sütunlarını (JavaFX TableColumn) oluştur
            for (int i = 1; i <= columnCount; i++) {
                final int j = i - 1; // ObservableList dizini (0 tabanlı)
                TableColumn<ObservableList<Object>, Object> col = new TableColumn<>(metaData.getColumnName(i));
                // Sütunun hangi değeri göstereceğini ayarla
                col.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(j)));
                dbTableView.getColumns().add(col); // Sütunu TableView'a ekle
            }

            // Satırları (verileri) doldur
            while (rs.next()) {
                ObservableList<Object> row = FXCollections.observableArrayList();
                // O satırdaki her sütunun verisini alıp listeye ekle
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                dbTableView.getItems().add(row); // Satırı TableView'a ekle
            }

        } catch (SQLException ex) {
            // Veri okunurken hata oluşursa kullanıcıya bildir
            showAlert("Hata", "Veri okuma hatası: " + ex.getMessage());
        }
    }

    /**
     * Metin alanına girilen özel bir SQL sorgusunu çalıştırır ve sonucunu gösterir.
     * 
     * @param query Çalıştırılacak SQL sorgusu
     */
    private void executeCustomQuery(String query) {
        // Sorgu boş veya geçersizse hiçbir şey yapma
        if (query == null || query.trim().isEmpty()) return;
        
        // Önceki tablonun görünümünü temizle
        dbTableView.getColumns().clear();
        dbTableView.getItems().clear();

        // Veritabanı bağlantısı kur
        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement()) {
             
            // Sorguyu çalıştır ve sonucun bir ResultSet döndürüp döndürmediğini kontrol et (örn. SELECT sorgusu)
            boolean isResultSet = stmt.execute(query);
            if (isResultSet) {
                // Eğer sorgu sonuç kümesi döndürdüyse (SELECT)
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    // Sütunları dinamik olarak oluştur
                    for (int i = 1; i <= columnCount; i++) {
                        final int j = i - 1;
                        TableColumn<ObservableList<Object>, Object> col = new TableColumn<>(metaData.getColumnName(i));
                        col.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(j)));
                        dbTableView.getColumns().add(col);
                    }

                    // Veri satırlarını doldur
                    while (rs.next()) {
                        ObservableList<Object> row = FXCollections.observableArrayList();
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(rs.getObject(i));
                        }
                        dbTableView.getItems().add(row);
                    }
                }
            } else {
                // Eğer sorgu veri değiştirme işlemiyse (INSERT, UPDATE, DELETE vb.), etkilenen satır sayısını al
                int updateCount = stmt.getUpdateCount();
                showAlert("Başarılı", "Sorgu başarıyla çalıştırıldı. Etkilenen satır sayısı: " + updateCount);
            }

        } catch (SQLException ex) {
            // SQL sorgusu hatalıysa kullanıcıya hata mesajını göster
            showAlert("Hata", "SQL Çalıştırma hatası: " + ex.getMessage());
        }
    }

    /**
     * Veritabanında daha önce oluşturulan tüm kullanıcı tablolarını siler.
     */
    private void resetDatabase() {
        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement()) {
            
            // Veritabanındaki tüm tablo isimlerini tutacak liste
            List<String> tableNames = new java.util.ArrayList<>();
            // Meta veriler üzerinden veritabanında var olan "TABLE" türündeki tüm tabloları çek
            try (ResultSet rs = conn.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // SQLite sistem tablolarını silmemek için "sqlite_" ile başlayanları atla
                    if (!tableName.startsWith("sqlite_")) {
                        tableNames.add(tableName);
                    }
                }
            }

            // Çekilen her bir kullanıcı tablosu için DROP TABLE sorgusunu çalıştır
            for (String tableName : tableNames) {
                stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            }
            
            // Arayüzdeki listeleri ve görünümleri temizle
            tablesListView.getItems().clear();
            dbTableView.getColumns().clear();
            dbTableView.getItems().clear();
            jsonTreeView.setRoot(null);
            
            // İşlem sonrası bilgi mesajı göster
            showAlert("Sıfırlandı", "Veritabanındaki tüm tablolar silindi.");
        } catch (SQLException ex) {
            // Sıfırlama işlemi sırasında hata oluşursa göster
            showAlert("Hata", "Sıfırlama hatası: " + ex.getMessage());
        }
    }

    /**
     * Kullanıcıya bilgi veya hata mesajı göstermek için kullanılan yardımcı bir metot.
     * 
     * @param title Mesaj penceresi başlığı
     * @param content Gösterilecek mesaj içeriği
     */
    private void showAlert(String title, String content) {
        // Bilgi tipinde bir uyarı kutusu (Alert) oluştur
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null); // Başlık metnini boş bırak
        alert.setContentText(content); // İçeriği ayarla
        alert.showAndWait(); // Kullanıcı kapatana kadar bekle
    }

    /**
     * Uygulamayı başlatan ana metod (JavaFX main çağırma kısmı).
     * @param args Komut satırı argümanları
     */
    public static void main(String[] args) {
        launch(args);
    }
}
