package org.proje2.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.proje2.model.Table;
import java.util.*;

/**
 * Core engine that converts hierarchical JSON into normalized Relational Tables.
 * Implements 1NF, 2NF, and 3NF logic via recursive tree traversal.
 */
public class JsonParser {
    private List<Table> tables = new ArrayList<>();
    private Map<String, Integer> tableIdCounters = new HashMap<>();

    /**
     * Entry point for parsing a JSON root node.
     * @param node The root JsonNode (can be Object or Array)
     * @return List of generated Table objects
     */
    public List<Table> parse(JsonNode node) {
        tables.clear();
        tableIdCounters.clear();

        Table mainTable = new Table("main_table");
        tables.add(mainTable);

        // Handle if the root of JSON is a list (multiple records)
        if (node.isArray()) {
            for (JsonNode element : node) {
                processRootElement(element, mainTable);
            }
        } else {
            processRootElement(node, mainTable);
        }

        return tables;
    }

    /**
     * Processes a single root-level object and starts recursion.
     */
    private void processRootElement(JsonNode node, Table mainTable) {
        Map<String, Object> row = new HashMap<>();
        int id = getNextId("main_table");
        mainTable.addColumn("row_id", "INTEGER");
        row.put("row_id", id);
        mainTable.getRows().add(row);
        parseNode(node, mainTable, row, "", id);
    }

    /**
     * Recursive method to traverse the JSON tree.
     * @param node Current node being processed
     * @param currentTable The table where simple fields will be added
     * @param currentRow The current row being populated
     * @param prefix Column prefix for flattened objects
     * @param parentId ID of the parent record for 1:N relations
     */
    private void parseNode(JsonNode node, Table currentTable, Map<String, Object> currentRow, String prefix, int parentId) {
        if (node.isObject()) {
            // Traverse all fields of the object
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String columnName = prefix.isEmpty() ? key : prefix + "_" + key;

                if (value.isObject()) {
                    if (isSimpleObject(value)) {
                        // Flattening: Simple nested objects are flattened into the parent table with a combined prefix
                        parseNode(value, currentTable, currentRow, columnName, parentId);
                    } else {
                        // 3NF: Complex nested objects (depth >= 2) are split into new tables to avoid transitive dependencies
                        Table subTable = findOrCreateTable(key);
                        Map<String, Object> subRow = new HashMap<>();
                        int subId = getNextId(key);

                        subTable.addColumn("row_id", "INTEGER");
                        subTable.addColumn("parent_row_id", "INTEGER");
                        subRow.put("row_id", subId);
                        subRow.put("parent_row_id", parentId);
                        subTable.getRows().add(subRow);

                        parseNode(value, subTable, subRow, "", subId);
                    }
                } else if (value.isArray()) {
                    // 1NF: Arrays are split into separate child tables for atomic values
                    Table subTable = findOrCreateTable(key);
                    for (JsonNode element : value) {
                        Map<String, Object> subRow = new HashMap<>();
                        int subId = getNextId(key);

                        subTable.addColumn("row_id", "INTEGER");
                        subTable.addColumn("parent_row_id", "INTEGER");
                        subRow.put("row_id", subId);
                        subRow.put("parent_row_id", parentId);
                        subTable.getRows().add(subRow);

                        parseNode(element, subTable, subRow, element.isContainerNode() ? "" : key, subId);
                    }
                } else {
                    // Simple value (String, Number, Boolean)
                    currentTable.addColumn(columnName, getSqlType(value));
                    currentRow.put(columnName, value.asText());
                }
            });
        } else {
            // Handling primitive value within an array
            String finalColumnName = prefix.isEmpty() ? "value" : prefix;
            currentTable.addColumn(finalColumnName, getSqlType(node));
            currentRow.put(finalColumnName, node.asText());
        }
    }

    private Table findOrCreateTable(String name) {
        for (Table t : tables) {
            if (t.getName().equals(name)) return t;
        }
        Table newTable = new Table(name);
        tables.add(newTable);
        return newTable;
    }

    private int getNextId(String tableName) {
        int id = tableIdCounters.getOrDefault(tableName, 0) + 1;
        tableIdCounters.put(tableName, id);
        return id;
    }

    private String getSqlType(JsonNode node) {
        if (node.isIntegralNumber()) return "INTEGER";
        if (node.isFloatingPointNumber()) return "REAL";
        if (node.isBoolean()) return "BOOLEAN";
        return "TEXT";
    }

    /**
     * Heuristic to determine if an object is simple (only primitives) for flattening,
     * or complex (contains objects/arrays) for 3NF table splitting.
     */
    private boolean isSimpleObject(JsonNode node) {
        if (!node.isObject()) return false;
        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            JsonNode child = elements.next();
            if (child.isObject() || child.isArray()) {
                return false; // Contains complex types, not simple
            }
        }
        return true;
    }
}
