package org.proje2.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite veritabanı bağlantısını yöneten sınıf.
 * Veritabanı bağlantısının oluşturulması ve yapılandırılmasından sorumludur.
 */
public class DatabaseManager {
    // Veritabanı dosyası proje kök dizininde 'database.db' adıyla oluşturulacaktır
    private static final String URL = "jdbc:sqlite:database.db";

    /**
     * SQLite veritabanına bağlantı kurar.
     * Bağlantı başarılı olursa Connection nesnesini, başarısız olursa null döndürür.
     * 
     * @return Başarılı bağlantı durumunda Connection nesnesi, aksi halde null.
     */
    public Connection connect() {
        Connection conn = null;
        try {
            // Verilen URL ile veritabanına bağlanmayı dene
            conn = DriverManager.getConnection(URL);
            System.out.println("SQLite bağlantısı başarılı");
        } catch (SQLException e) {
            // Bağlantı sırasında hata oluşursa hatayı ekrana yazdır
            System.out.println("Bağlantı hatası: " + e.getMessage());
        }
        return conn; // Bağlantı nesnesini döndür
    }
}
