package org.proje2.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a database table structure extracted from JSON.
 * Holds table name, columns with their types, and row data.
 */
public class Table {
    private String name;
    // Using LinkedHashMap to preserve column insertion order
    private Map<String, String> columns = new LinkedHashMap<>();
    private List<Map<String, Object>> rows = new ArrayList<>();

    public Table(String name) {
        this.name = name;
    }

    /**
     * Adds a column to the table if it doesn't already exist.
     * @param columnName Name of the column
     * @param type SQL data type (TEXT, INTEGER, etc.)
     */
    public void addColumn(String columnName, String type) {
        columns.put(columnName, type);
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    @Override
    public String toString() {
        return "Table{" +
                "name='" + name + '\'' +
                ", columns=" + columns +
                ", rowsCount=" + rows.size() +
                '}';
    }
}
