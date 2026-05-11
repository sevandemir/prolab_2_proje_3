package org.proje2.converter;

import org.proje2.database.DatabaseManager;
import org.proje2.model.Table;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates and executes SQL statements (DDL and DML) in the SQLite database.
 * Optimized with Transactions and Batch processing for large datasets.
 */
public class SqlGenerator {
    private final DatabaseManager dbManager = new DatabaseManager();

    /**
     * Exports the list of Table objects to the physical database.
     * @param tables List of tables to be created and populated.
     */
    public void exportToDatabase(List<Table> tables) {
        Connection conn = dbManager.connect();
        if (conn == null) return;

        try {
            // Start transaction for high-performance inserts
            conn.setAutoCommit(false);

            // 1. Drop existing tables to start fresh (Reset functionality)
            dropAllTables(conn, tables);

            // 2. Create tables based on discovered schema
            for (Table table : tables) {
                createTable(conn, table);
            }

            // 3. Insert data into tables using batch processing
            for (Table table : tables) {
                insertData(conn, table);
            }

            // Commit all changes at once
            conn.commit();
            System.out.println("Database export completed successfully.");

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.err.println("SQL Error: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void dropAllTables(Connection conn, List<Table> tables) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (Table table : tables) {
                stmt.execute("DROP TABLE IF EXISTS " + table.getName());
            }
        }
    }

    private void createTable(Connection conn, Table table) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(table.getName())
                .append(" (");

        List<String> columns = table.getColumns().entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.toList());

        sql.append(String.join(", ", columns));
        sql.append(")");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    /**
     * Inserts rows using PreparedStatement and Batching to improve performance.
     */
    private void insertData(Connection conn, Table table) throws SQLException {
        if (table.getRows().isEmpty()) return;

        String columns = String.join(", ", table.getColumns().keySet());
        String placeholders = table.getColumns().keySet().stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + table.getName() + " (" + columns + ") VALUES (" + placeholders + ")";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map<String, Object> row : table.getRows()) {
                int i = 1;
                for (String colName : table.getColumns().keySet()) {
                    pstmt.setObject(i++, row.get(colName));
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
}
