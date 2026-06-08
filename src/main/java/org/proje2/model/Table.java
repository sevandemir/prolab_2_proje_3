package org.proje2.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON'dan çıkarılan ve veritabanına kaydedilecek olan tablo yapısını temsil eder.
 * Tablo adını, veri tipleriyle birlikte sütunlarını ve satır verilerini tutar.
 */
public class Table {
    // Tablonun adı
    private String name;
    
    // Sütunların eklenme sırasını korumak için LinkedHashMap kullanıyoruz
    // Anahtar: Sütun adı, Değer: Sütunun SQL veri tipi (TEXT, INTEGER vb.)
    private Map<String, String> columns = new LinkedHashMap<>();
    
    // Tablodaki her bir satır verisini tutan liste
    // Her satır, sütun adını anahtar ve değerini barındıran bir Map'tir
    private List<Map<String, Object>> rows = new ArrayList<>();

    /**
     * Belirtilen isimle yeni bir tablo nesnesi oluşturur.
     * 
     * @param name Tablonun adı
     */
    public Table(String name) {
        this.name = name;
    }

    /**
     * Tabloya yeni bir sütun ekler (eğer zaten mevcut değilse, haritada tutulur).
     * 
     * @param columnName Eklenecek sütunun adı
     * @param type Sütunun SQL veri tipi (TEXT, INTEGER vb.)
     */
    public void addColumn(String columnName, String type) {
        // Sütun adını ve tipini columns haritasına ekle
        columns.put(columnName, type);
    }

    /**
     * Tablonun adını döndürür.
     * 
     * @return Tablo adı
     */
    public String getName() {
        return name;
    }

    /**
     * Tablonun sütunlarını ve veri tiplerini içeren haritayı döndürür.
     * 
     * @return Sütun adı - veri tipi haritası
     */
    public Map<String, String> getColumns() {
        return columns;
    }

    /**
     * Tablodaki tüm satırları döndürür.
     * 
     * @return Satır verilerini içeren liste
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /**
     * Tablo nesnesinin metinsel gösterimini (String) döndürür.
     * Hata ayıklama ve loglama için kullanılır.
     * 
     * @return Tablonun string temsili
     */
    @Override
    public String toString() {
        return "Table{" +
                "name='" + name + '\'' +
                ", columns=" + columns +
                ", rowsCount=" + rows.size() +
                '}';
    }
}
