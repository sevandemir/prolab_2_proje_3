package org.proje2.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.proje2.model.Table;
import java.util.*;

/**
 * Hiyerarşik JSON yapısını Normalleştirilmiş (Normalized) İlişkisel Tablolara dönüştüren çekirdek motor.
 * Öz yinelemeli (recursive) ağaç dolaşımıyla 1NF, 2NF ve 3NF mantığını uygular.
 */
public class JsonParser {
    // Üretilen tüm tabloları sakladığımız liste
    private List<Table> tables = new ArrayList<>();
    
    // Her tablo için bir sonraki ID değerini tutan sayaç haritası (Auto-increment simülasyonu)
    private Map<String, Integer> tableIdCounters = new HashMap<>();

    /**
     * Kök (root) JSON düğümünü (node) çözümlemek için başlangıç metodudur.
     * 
     * @param node Kök JsonNode nesnesi (Nesne veya Dizi olabilir)
     * @return Üretilen Table nesnelerinin listesi
     */
    public List<Table> parse(JsonNode node) {
        // Önceki çözümlemelerden kalan verileri temizle
        tables.clear();
        tableIdCounters.clear();

        // En üst seviye JSON verilerini tutacak 'main_table' (ana tablo) oluştur
        Table mainTable = new Table("main_table");
        tables.add(mainTable);

        // JSON kökü bir listeyse (birden fazla kayıt içeriyorsa)
        if (node.isArray()) {
            for (JsonNode element : node) {
                processRootElement(element, mainTable); // Her bir elemanı ayrı ayrı işle
            }
        } else {
            // JSON kökü tek bir nesneyse doğrudan işle
            processRootElement(node, mainTable);
        }

        return tables; // Oluşturulan tabloların listesini döndür
    }

    /**
     * Kök düzeyindeki tek bir nesneyi işler ve özyinelemeli çözümlemeyi başlatır.
     * 
     * @param node İşlenecek JSON düğümü
     * @param mainTable Verilerin kaydedileceği ana tablo
     */
    private void processRootElement(JsonNode node, Table mainTable) {
        // Ana tablodaki geçerli satırı temsil eden bir Map oluştur
        Map<String, Object> row = new HashMap<>();
        
        // Yeni satır için benzersiz bir 'row_id' (satır kimliği) al
        int id = getNextId("main_table");
        
        // Ana tabloya 'row_id' adında bir tamsayı sütunu ekle
        mainTable.addColumn("row_id", "INTEGER");
        row.put("row_id", id);
        
        // Yeni satırı ana tablonun satırları arasına ekle
        mainTable.getRows().add(row);
        
        // JSON düğümünü çözümlemeye başla
        parseNode(node, mainTable, row, "", id);
    }

    /**
     * JSON ağacını dolaşmak ve verileri tablolara yerleştirmek için özyinelemeli (recursive) metot.
     * 
     * @param node İşlenmekte olan geçerli JSON düğümü
     * @param currentTable Basit alanların ekleneceği geçerli tablo
     * @param currentRow Verilerin doldurulduğu geçerli satır
     * @param prefix Düzleştirilmiş (flattened) nesneler için kullanılacak sütun adı öneki
     * @param parentId 1:N (Bire-Çok) ilişkiler için üst (parent) kaydın kimliği
     */
    private void parseNode(JsonNode node, Table currentTable, Map<String, Object> currentRow, String prefix, int parentId) {
        if (node.isObject()) {
            // Nesnenin tüm alanlarını (key-value çiftlerini) sırayla dolaş
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey(); // JSON anahtarı
                JsonNode value = entry.getValue(); // JSON değeri
                
                // Eğer bir önek varsa sütun adını "önek_anahtar" yap, yoksa sadece "anahtar" adını kullan
                String columnName = prefix.isEmpty() ? key : prefix + "_" + key;

                if (value.isObject()) {
                    // Eğer değer bir nesneyse (nested object)
                    if (isSimpleObject(value)) {
                        // Düzleştirme (Flattening): Sadece basit tipler içeren iç içe nesneler,
                        // sütun isimleri birleştirilerek üst tabloya düzleştirilir.
                        parseNode(value, currentTable, currentRow, columnName, parentId);
                    } else {
                        // 3NF (Üçüncü Normal Form): Karmaşık iç içe nesneler (derinlik >= 2), 
                        // geçişli bağımlılıkları önlemek için yeni alt tablolara ayrılır.
                        Table subTable = findOrCreateTable(key);
                        Map<String, Object> subRow = new HashMap<>();
                        int subId = getNextId(key);

                        // Alt tabloya kendi ID'si ve üst tablonun ID'si (Foreign Key) sütunları eklenir
                        subTable.addColumn("row_id", "INTEGER");
                        subTable.addColumn("parent_row_id", "INTEGER");
                        subRow.put("row_id", subId);
                        subRow.put("parent_row_id", parentId); // İlişkiyi sağlamak için parent_row_id set edilir
                        subTable.getRows().add(subRow);

                        // Nesnenin içeriği yeni tablo üzerinde çözümlenir
                        parseNode(value, subTable, subRow, "", subId);
                    }
                } else if (value.isArray()) {
                    // 1NF (Birinci Normal Form): Diziler (arrays), atomik değerler sağlamak
                    // amacıyla ayrı alt tablolara (child tables) ayrılır.
                    Table subTable = findOrCreateTable(key);
                    for (JsonNode element : value) {
                        Map<String, Object> subRow = new HashMap<>();
                        int subId = getNextId(key);

                        // Alt tabloya kendi ID'si ve üst tablonun ID'si (Foreign Key) sütunları eklenir
                        subTable.addColumn("row_id", "INTEGER");
                        subTable.addColumn("parent_row_id", "INTEGER");
                        subRow.put("row_id", subId);
                        subRow.put("parent_row_id", parentId); // Üst kayıtla bağlantı
                        subTable.getRows().add(subRow);

                        // Dizinin elemanı çözümlemeye gönderilir (Eğer basit tipse columnName, karmaşıksa nesne kendi çözümlenir)
                        parseNode(element, subTable, subRow, element.isContainerNode() ? "" : key, subId);
                    }
                } else {
                    // Basit değer (String, Number, Boolean)
                    // Sütunu tabloya ekle ve değeri geçerli satıra yaz
                    currentTable.addColumn(columnName, getSqlType(value));
                    currentRow.put(columnName, value.asText());
                }
            });
        } else {
            // Bir dizi içinde bulunan ilkel (primitive) değeri (örneğin sayı, metin) işleme
            String finalColumnName = prefix.isEmpty() ? "value" : prefix;
            currentTable.addColumn(finalColumnName, getSqlType(node));
            currentRow.put(finalColumnName, node.asText());
        }
    }

    /**
     * Verilen isme sahip bir tablo arar, bulursa döndürür; bulamazsa yeni oluşturur.
     * 
     * @param name Aranacak veya oluşturulacak tablonun adı
     * @return Bulunan veya yeni oluşturulan tablo
     */
    private Table findOrCreateTable(String name) {
        // Var olan tabloları kontrol et
        for (Table t : tables) {
            if (t.getName().equals(name)) return t;
        }
        // Yoksa yeni bir tablo oluştur ve listeye ekle
        Table newTable = new Table(name);
        tables.add(newTable);
        return newTable;
    }

    /**
     * Belirtilen tablo için otomatik artan bir sonraki kimlik numarasını (ID) üretir.
     * 
     * @param tableName ID'nin üretileceği tablonun adı
     * @return Üretilen yeni ID
     */
    private int getNextId(String tableName) {
        // Sayaçtan mevcut değeri alıp 1 artır
        int id = tableIdCounters.getOrDefault(tableName, 0) + 1;
        tableIdCounters.put(tableName, id);
        return id;
    }

    /**
     * JSON değerine karşılık gelen SQLite veri tipini belirler.
     * 
     * @param node Tipin belirleneceği JSON düğümü
     * @return SQL veri tipi (INTEGER, REAL, BOOLEAN veya TEXT)
     */
    private String getSqlType(JsonNode node) {
        if (node.isIntegralNumber()) return "INTEGER";       // Tam sayı
        if (node.isFloatingPointNumber()) return "REAL";     // Ondalıklı sayı
        if (node.isBoolean()) return "BOOLEAN";              // Mantıksal değer
        return "TEXT";                                       // Diğer durumlar (Metin vb.)
    }

    /**
     * Bir nesnenin düzleştirme (flattening) için basit (yalnızca ilkel veri tipleri)
     * mi, yoksa 3NF'ye göre tablo bölünmesi için karmaşık (nesne/dizi içeriyor)
     * mı olduğunu belirleyen sezgisel bir algoritma (heuristic).
     * 
     * @param node Kontrol edilecek JSON düğümü
     * @return Basit nesne ise true, karmaşık nesne ise false
     */
    private boolean isSimpleObject(JsonNode node) {
        // Eğer düğüm nesne değilse, basit değildir
        if (!node.isObject()) return false;
        
        // Nesnenin alt elemanlarını kontrol et
        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            JsonNode child = elements.next();
            // Alt elemanlardan herhangi biri nesne veya diziyse, bu karmaşık bir yapıdır
            if (child.isObject() || child.isArray()) {
                return false; // Karmaşık tipler içeriyor
            }
        }
        // Sadece basit (ilkel) tipler içeriyor
        return true;
    }
}
