package org.proje2.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages the SQLite database connection.
 */
public class DatabaseManager {
    // Database file will be created in the project root
    private static final String URL = "jdbc:sqlite:database.db";

    /**
     * Establishes a connection to the SQLite database.
     * @return Connection object if successful, null otherwise.
     */
    public Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL);
            System.out.println("SQLite connection successful");
        } catch (SQLException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
        return conn;
    }
}
