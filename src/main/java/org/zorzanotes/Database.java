package org.zorzanotes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final Path DB_PATH =
            determineDatabasePath();

    private static final String DB_URL =
            "jdbc:sqlite:" + DB_PATH.toAbsolutePath();

    public static Connection connect()
            throws SQLException {

        ensureApplicationDirectory();

        Connection connection =
                DriverManager.getConnection(DB_URL);

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    "PRAGMA foreign_keys = ON"
            );
        }

        return connection;
    }

    public static void initialize() {

        ensureApplicationDirectory();
        migrateDevelopmentDatabase();

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

        String notesFtsTable = """
                CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts
                USING fts5(
                    title,
                    body,
                    content='notes',
                    content_rowid='id',
                    tokenize='unicode61'
                )
                """;

        String notesFtsInsertTrigger = """
                CREATE TRIGGER IF NOT EXISTS notes_fts_insert
                AFTER INSERT ON notes
                BEGIN
                    INSERT INTO notes_fts(
                        rowid,
                        title,
                        body
                    )
                    VALUES (
                        new.id,
                        new.title,
                        new.body
                    );
                END
                """;

        String notesFtsDeleteTrigger = """
                CREATE TRIGGER IF NOT EXISTS notes_fts_delete
                AFTER DELETE ON notes
                BEGIN
                    INSERT INTO notes_fts(
                        notes_fts,
                        rowid,
                        title,
                        body
                    )
                    VALUES (
                        'delete',
                        old.id,
                        old.title,
                        old.body
                    );
                END
                """;

        String notesFtsUpdateTrigger = """
                CREATE TRIGGER IF NOT EXISTS notes_fts_update
                AFTER UPDATE ON notes
                BEGIN
                    INSERT INTO notes_fts(
                        notes_fts,
                        rowid,
                        title,
                        body
                    )
                    VALUES (
                        'delete',
                        old.id,
                        old.title,
                        old.body
                    );

                    INSERT INTO notes_fts(
                        rowid,
                        title,
                        body
                    )
                    VALUES (
                        new.id,
                        new.title,
                        new.body
                    );
                END
                """;

        try (Connection connection = connect();
             Statement statement =
                     connection.createStatement()) {

            statement.execute(notebooksTable);
            statement.execute(notesTable);
            statement.execute(tagsTable);
            statement.execute(noteTagsTable);

            statement.execute(notesFtsTable);

            statement.execute(
                    notesFtsInsertTrigger
            );

            statement.execute(
                    notesFtsDeleteTrigger
            );

            statement.execute(
                    notesFtsUpdateTrigger
            );

            statement.execute("""
                    INSERT INTO notes_fts(notes_fts)
                    VALUES ('rebuild')
                    """);

            System.out.println(
                    "Zorza database initialized."
            );

            System.out.println(
                    "Database: " +
                            DB_PATH.toAbsolutePath()
            );

            System.out.println(
                    "Zorza FTS5 search index ready."
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to initialize Zorza database.",
                    e
            );
        }
    }

    private static Path determineDatabasePath() {

        String os =
                System.getProperty("os.name")
                        .toLowerCase();

        String home =
                System.getProperty("user.home");

        if (os.contains("mac")) {

            return Path.of(
                    home,
                    "Library",
                    "Application Support",
                    "Zorza Notes",
                    "zorza.db"
            );
        }

        if (os.contains("win")) {

            String localAppData =
                    System.getenv(
                            "LOCALAPPDATA"
                    );

            if (localAppData != null &&
                    !localAppData.isBlank()) {

                return Path.of(
                        localAppData,
                        "Zorza Notes",
                        "zorza.db"
                );
            }

            return Path.of(
                    home,
                    "AppData",
                    "Local",
                    "Zorza Notes",
                    "zorza.db"
            );
        }

        String xdgDataHome =
                System.getenv(
                        "XDG_DATA_HOME"
                );

        if (xdgDataHome != null &&
                !xdgDataHome.isBlank()) {

            return Path.of(
                    xdgDataHome,
                    "zorza-notes",
                    "zorza.db"
            );
        }

        return Path.of(
                home,
                ".local",
                "share",
                "zorza-notes",
                "zorza.db"
        );
    }

    private static void ensureApplicationDirectory() {

        try {

            Files.createDirectories(
                    DB_PATH.getParent()
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to create Zorza data directory.",
                    e
            );
        }
    }

    private static void migrateDevelopmentDatabase() {

        Path oldDatabase =
                Path.of("zorza.db")
                        .toAbsolutePath()
                        .normalize();

        Path newDatabase =
                DB_PATH.toAbsolutePath()
                        .normalize();

        if (oldDatabase.equals(newDatabase)) {
            return;
        }

        if (!Files.exists(oldDatabase)) {
            return;
        }

        if (Files.exists(newDatabase)) {
            return;
        }

        try {

            Files.copy(
                    oldDatabase,
                    newDatabase,
                    StandardCopyOption.COPY_ATTRIBUTES
            );

            System.out.println(
                    "Existing Zorza database copied to:"
            );

            System.out.println(
                    newDatabase
            );

            /*
             * Deliberately leave the old database alone.
             *
             * Once the user verifies that everything migrated
             * correctly, the old development copy can be removed.
             */

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to migrate existing Zorza database.",
                    e
            );
        }
    }

    public static Path getDatabasePath() {
        return DB_PATH;
    }
}