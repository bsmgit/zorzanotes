package org.zorzanotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:zorza.db";

    public static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    public static void initialize() {

        String notebooksTable = """
            CREATE TABLE IF NOT EXISTS notebooks (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid        TEXT NOT NULL UNIQUE,
                name        TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                updated_at  TEXT NOT NULL
            )
            """;

        String notesTable = """
            CREATE TABLE IF NOT EXISTS notes (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid         TEXT NOT NULL UNIQUE,
                notebook_id  INTEGER NOT NULL,
                title        TEXT NOT NULL DEFAULT '',
                body         TEXT NOT NULL DEFAULT '',
                created_at   TEXT NOT NULL,
                updated_at   TEXT NOT NULL,

                FOREIGN KEY (notebook_id)
                    REFERENCES notebooks(id)
                    ON DELETE CASCADE
            )
            """;

        String tagsTable = """
            CREATE TABLE IF NOT EXISTS tags (
                id    INTEGER PRIMARY KEY AUTOINCREMENT,
                name  TEXT NOT NULL UNIQUE
            )
            """;

        String noteTagsTable = """
            CREATE TABLE IF NOT EXISTS note_tags (
                note_id INTEGER NOT NULL,
                tag_id  INTEGER NOT NULL,

                PRIMARY KEY (note_id, tag_id),

                FOREIGN KEY (note_id)
                    REFERENCES notes(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (tag_id)
                    REFERENCES tags(id)
                    ON DELETE CASCADE
            )
            """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.execute(notebooksTable);
            statement.execute(notesTable);
            statement.execute(tagsTable);
            statement.execute(noteTagsTable);

            System.out.println("Zorza database initialized.");

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Unable to initialize Zorza database.", e
            );
        }
    }
}