package org.proje2.converter;

import org.proje2.database.DatabaseManager;
import org.proje2.model.Table;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQLite veritabanında SQL ifadelerini (DDL ve DML) üreten ve çalıştıran sınıf.
 * Büyük veri kümeleri için yüksek performans sağlamak amacıyla İşlem (Transaction)
 * ve Toplu (Batch) işleme mekanizmalarıyla optimize edilmiştir.
 */
public class SqlGenerator {
    // Veritabanı bağlantı yöneticisi örneği
    private final DatabaseManager dbManager = new DatabaseManager();

    /**
     * Bellekteki Table nesneleri listesini fiziksel veritabanına aktarır.
     * 
     * @param tables Oluşturulacak ve verilerle doldurulacak tabloların listesi
     */
    public void exportToDatabase(List<Table> tables) {
        // Veritabanı bağlantısı kur
        Connection conn = dbManager.connect();
        if (conn == null) return; // Bağlantı başarısızsa işlemi iptal et

        try {
            // Yüksek performanslı veri ekleme için otomatik işlem onamayı (auto-commit) kapat
            conn.setAutoCommit(false);

            // 1. Temiz bir başlangıç yapmak için mevcut tabloları sil (Sıfırlama işlevi)
            dropAllTables(conn, tables);

            // 2. Çıkarılan şemaya göre yeni tabloları oluştur
            for (Table table : tables) {
                createTable(conn, table);
            }

            // 3. Toplu işlem (batch processing) kullanarak tablolara verileri ekle
            for (Table table : tables) {
                insertData(conn, table);
            }

            // Yapılan tüm değişiklikleri tek seferde onayla (commit) ve kaydet
            conn.commit();
            System.out.println("Database export completed successfully.");

        } catch (SQLException e) {
            // Bir hata oluşursa, işlemleri geri al (rollback)
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.err.println("SQL Error: " + e.getMessage());
        } finally {
            // Bağlantıyı kapatmadan önce otomatik onamayı tekrar aç
            try {
                conn.setAutoCommit(true);
                conn.close(); // Veritabanı bağlantısını güvenli bir şekilde kapat
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Verilen tablolara ait mevcut veritabanı tablolarını siler.
     * 
     * @param conn Veritabanı bağlantısı
     * @param tables Silinecek tabloların listesi
     * @throws SQLException SQL çalıştırma sırasında hata oluşursa fırlatılır
     */
    private void dropAllTables(Connection conn, List<Table> tables) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Listedeki her bir tablo için DROP TABLE ifadesi çalıştır
            for (Table table : tables) {
                stmt.execute("DROP TABLE IF EXISTS " + table.getName());
            }
        }
    }

    /**
     * Verilen Table nesnesinin şemasına uygun fiziksel veritabanı tablosu oluşturur.
     * 
     * @param conn Veritabanı bağlantısı
     * @param table Oluşturulacak tablonun modeli
     * @throws SQLException SQL çalıştırma sırasında hata oluşursa fırlatılır
     */
    private void createTable(Connection conn, Table table) throws SQLException {
        // CREATE TABLE ifadesini oluşturmaya başla
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(table.getName())
                .append(" (");

        // Tablonun sütun isimleri ve veri tiplerini SQL sözdizimi formatına dönüştür
        List<String> columns = table.getColumns().entrySet().stream()
                .map(e -> {
                    String colDef = e.getKey() + " " + e.getValue();
                    // Eğer sütun "row_id" ise onu BİRİNCİL ANAHTAR (PRIMARY KEY) yap
                    if (e.getKey().equals("row_id")) {
                        colDef += " PRIMARY KEY";
                    }
                    return colDef;
                })
                .collect(Collectors.toList());

        // Sütun tanımlamalarını virgülle birleştir ve sorguya ekle
        sql.append(String.join(", ", columns));
        sql.append(")");

        // Oluşturulan SQL DDL ifadesini çalıştır
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    /**
     * PreparedStatement ve Batch (toplu) işlemi kullanarak tabloya verileri ekler.
     * Bu yöntem, tek tek eklemeye göre performansı önemli ölçüde artırır.
     * 
     * @param conn Veritabanı bağlantısı
     * @param table İçine veri eklenecek tablo
     * @throws SQLException SQL çalıştırma sırasında hata oluşursa fırlatılır
     */
    private void insertData(Connection conn, Table table) throws SQLException {
        // Eğer tabloda eklenecek satır yoksa işlem yapmadan çık
        if (table.getRows().isEmpty()) return;

        // Eklenecek sütunların adlarını virgülle birleştirerek oluştur
        String columns = String.join(", ", table.getColumns().keySet());
        
        // Sütun sayısı kadar soru işareti (?) oluştur (PreparedStatement için parametreler)
        String placeholders = table.getColumns().keySet().stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));

        // INSERT INTO SQL ifadesini oluştur
        String sql = "INSERT INTO " + table.getName() + " (" + columns + ") VALUES (" + placeholders + ")";

        // Önceden derlenmiş SQL ifadesini kullanarak verileri ekle
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Tablodaki her bir satır verisi için döngü oluştur
            for (Map<String, Object> row : table.getRows()) {
                int i = 1;
                // Her bir sütun için satırdaki ilgili veriyi al ve parametreye ata
                for (String colName : table.getColumns().keySet()) {
                    pstmt.setObject(i++, row.get(colName));
                }
                // Ayarlanan parametrelerle birlikte satırı toplu işlem kuyruğuna (batch) ekle
                pstmt.addBatch();
            }
            // Kuyruktaki tüm satır ekleme işlemlerini veritabanında topluca çalıştır
            pstmt.executeBatch();
        }
    }
}
