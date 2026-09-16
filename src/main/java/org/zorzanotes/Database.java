package org.zorzanotes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class Database {

    private static final String DATABASE_FILE = "zorza.db";

    /*
     * This is intentionally retained after migration.
     *
     * IT CONTAINS PLAINTEXT DATA.
     *
     * Zorza must never automatically delete this file.
     */
    private static final String PLAINTEXT_BACKUP_FILE =
            "zorza-plaintext-backup.db";

    private static final String ENCRYPTED_TEMP_FILE =
            "zorza-encrypted-migration.db";

    private static final Path APPLICATION_DIRECTORY =
            determineApplicationDirectory();

    private static final Path DATABASE_PATH =
            APPLICATION_DIRECTORY.resolve(DATABASE_FILE);

    private static final Path PLAINTEXT_BACKUP_PATH =
            APPLICATION_DIRECTORY.resolve(PLAINTEXT_BACKUP_FILE);

    private static final Path ENCRYPTED_TEMP_PATH =
            APPLICATION_DIRECTORY.resolve(ENCRYPTED_TEMP_FILE);

    private Database() {
    }

    public static Path getDatabasePath() {
        return DATABASE_PATH;
    }

    public static Path getPlaintextBackupPath() {
        return PLAINTEXT_BACKUP_PATH;
    }

    public static boolean databaseExists() {
        return Files.exists(DATABASE_PATH);
    }

    public static boolean databaseIsPlaintext() {
        return Files.exists(DATABASE_PATH) && isPlaintextSQLiteDatabase(DATABASE_PATH);
    }

    public static Connection connect() throws SQLException {
        ensureApplicationDirectory();

        String url =
                "jdbc:sqlite:" +
                        DATABASE_PATH.toAbsolutePath();

        return VaultService.openEncryptedConnection(url);
    }

    public static void initialize() throws SQLException {
        ensureApplicationDirectory();

        /*
         * Before opening the production database as encrypted, determine
         * whether an existing database is plaintext.
         */
        if (Files.exists(DATABASE_PATH)) {

            if (isPlaintextSQLiteDatabase(DATABASE_PATH)) {
                migratePlaintextDatabase();
            }
        }

        /*
         * At this point:
         *
         * 1. the database did not exist and will be created encrypted, or
         * 2. the existing plaintext database was migrated, or
         * 3. the database was already encrypted.
         */
        try (Connection connection = connect()) {

            createSchema(connection);
            verifyDatabaseIntegrity(connection);
        }

        System.out.println(
                "Zorza database: " +
                        DATABASE_PATH.toAbsolutePath()
        );
    }

    /*
     * ---------------------------------------------------------
     * DATABASE SCHEMA
     * ---------------------------------------------------------
     */

    private static void createSchema(
            Connection connection
    ) throws SQLException {

        try (Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS notebooks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    notebook_id INTEGER NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (notebook_id)
                        REFERENCES notebooks(id)
                        ON DELETE CASCADE
                )
                """);

            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_notes_notebook_id
                ON notes(notebook_id)
                """);

            statement.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts
                USING fts5(
                    title,
                    body,
                    content='notes',
                    content_rowid='id',
                    tokenize='unicode61'
                )
                """);

            statement.execute("""
                CREATE TRIGGER IF NOT EXISTS notes_ai
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
                """);

            statement.execute("""
                CREATE TRIGGER IF NOT EXISTS notes_ad
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
                """);

            statement.execute("""
                CREATE TRIGGER IF NOT EXISTS notes_au
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
                """);

            /*
             * Keep the FTS index synchronized.
             */
            statement.execute("""
                INSERT INTO notes_fts(notes_fts)
                VALUES ('rebuild')
                """);
        }
    }

    /*
     * ---------------------------------------------------------
     * PLAINTEXT DETECTION
     * ---------------------------------------------------------
     */

    private static boolean isPlaintextSQLiteDatabase(
            Path path
    ) {

        if (!Files.exists(path)) {
            return false;
        }

        /*
         * A normal SQLite database begins with:
         *
         * SQLite format 3\0
         *
         * An encrypted ChaCha20 database will not expose this header.
         */
        byte[] expected = new byte[] {
                'S', 'Q', 'L', 'i', 't', 'e', ' ',
                'f', 'o', 'r', 'm', 'a', 't', ' ',
                '3', 0
        };

        try {
            byte[] header = new byte[16];

            try (var input = Files.newInputStream(path)) {

                int read = input.read(header);

                if (read != 16) {
                    return false;
                }
            }

            for (int i = 0; i < expected.length; i++) {
                if (header[i] != expected[i]) {
                    return false;
                }
            }

            return true;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect Zorza database.",
                    exception
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * PLAINTEXT -> ENCRYPTED MIGRATION
     * ---------------------------------------------------------
     */

    private static void migratePlaintextDatabase() throws SQLException {
        System.out.println("Existing plaintext Zorza database detected.");
        System.out.println("Beginning safe encrypted migration...");

        createPlaintextSafetyBackup();

        try {
            Files.deleteIfExists(ENCRYPTED_TEMP_PATH);
            Files.deleteIfExists(Path.of(ENCRYPTED_TEMP_PATH.toString() + "-wal"));
            Files.deleteIfExists(Path.of(ENCRYPTED_TEMP_PATH.toString() + "-shm"));
        } catch (IOException e) {
            throw new SQLException("Unable to prepare encrypted migration database.", e);
        }

        String sourceUrl = "jdbc:sqlite:" + DATABASE_PATH.toAbsolutePath();
        String destinationUrl = "jdbc:sqlite:" + ENCRYPTED_TEMP_PATH.toAbsolutePath();
        long sourceNotebookCount;
        long sourceNoteCount;

        try (Connection source = DriverManager.getConnection(sourceUrl);
             Connection destination = VaultService.openEncryptedConnection(destinationUrl)) {

            try (Statement statement = source.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            createMigrationTables(destination);
            sourceNotebookCount = tableCount(source, "notebooks");
            sourceNoteCount = tableCount(source, "notes");

            destination.setAutoCommit(false);
            try {
                copyNotebooks(source, destination);
                copyNotes(source, destination);
                destination.commit();
            } catch (SQLException e) {
                destination.rollback();
                throw e;
            } finally {
                destination.setAutoCommit(true);
            }

            createSearchSchema(destination);
            verifyDatabaseIntegrity(destination);

            if (tableCount(destination, "notebooks") != sourceNotebookCount) {
                throw new SQLException("Notebook migration verification failed.");
            }
            if (tableCount(destination, "notes") != sourceNoteCount) {
                throw new SQLException("Note migration verification failed.");
            }
            if (tableCount(destination, "notes_fts") != sourceNoteCount) {
                throw new SQLException("Search-index migration verification failed.");
            }
        }

        verifyEncryptedMigration(sourceNotebookCount, sourceNoteCount);
        replacePlaintextDatabase();

        System.out.println("Zorza database encryption migration completed successfully.");
        System.out.println("PLAINTEXT SAFETY BACKUP: " + PLAINTEXT_BACKUP_PATH.toAbsolutePath());
        System.out.println("IMPORTANT: This backup is NOT encrypted. Delete it manually only after verifying your notes.");
    }

    private static void createMigrationTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE notebooks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            statement.execute("""
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    notebook_id INTEGER NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (notebook_id) REFERENCES notebooks(id) ON DELETE CASCADE
                )
                """);
            statement.execute("CREATE INDEX idx_notes_notebook_id ON notes(notebook_id)");
        }
    }

    private static void createSearchSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE VIRTUAL TABLE notes_fts
                USING fts5(title, body, content='notes', content_rowid='id', tokenize='unicode61')
                """);
            statement.execute("""
                CREATE TRIGGER notes_ai AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """);
            statement.execute("""
                CREATE TRIGGER notes_ad AFTER DELETE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, rowid, title, body)
                    VALUES ('delete', old.id, old.title, old.body);
                END
                """);
            statement.execute("""
                CREATE TRIGGER notes_au AFTER UPDATE ON notes BEGIN
                    INSERT INTO notes_fts(notes_fts, rowid, title, body)
                    VALUES ('delete', old.id, old.title, old.body);
                    INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
                END
                """);
            statement.execute("INSERT INTO notes_fts(notes_fts) VALUES ('rebuild')");
        }
    }

    private static void copyNotebooks(Connection source, Connection destination) throws SQLException {
        String selectSql = "SELECT id, uuid, name, created_at, updated_at FROM notebooks ORDER BY id";
        String insertSql = "INSERT INTO notebooks(id, uuid, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement select = source.prepareStatement(selectSql);
             ResultSet results = select.executeQuery();
             PreparedStatement insert = destination.prepareStatement(insertSql)) {
            while (results.next()) {
                insert.setLong(1, results.getLong("id"));
                insert.setString(2, results.getString("uuid"));
                insert.setString(3, results.getString("name"));
                insert.setString(4, results.getString("created_at"));
                insert.setString(5, results.getString("updated_at"));
                insert.executeUpdate();
            }
        }
    }

    private static void copyNotes(Connection source, Connection destination) throws SQLException {
        String selectSql = "SELECT id, uuid, notebook_id, title, body, created_at, updated_at FROM notes ORDER BY id";
        String insertSql = "INSERT INTO notes(id, uuid, notebook_id, title, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement select = source.prepareStatement(selectSql);
             ResultSet results = select.executeQuery();
             PreparedStatement insert = destination.prepareStatement(insertSql)) {
            while (results.next()) {
                insert.setLong(1, results.getLong("id"));
                insert.setString(2, results.getString("uuid"));
                insert.setLong(3, results.getLong("notebook_id"));
                insert.setString(4, results.getString("title"));
                insert.setString(5, results.getString("body"));
                insert.setString(6, results.getString("created_at"));
                insert.setString(7, results.getString("updated_at"));
                insert.executeUpdate();
            }
        }
    }

    private static void createPlaintextSafetyBackup() throws SQLException {
        if (Files.exists(PLAINTEXT_BACKUP_PATH)) {
            System.out.println("Existing plaintext safety backup preserved: " + PLAINTEXT_BACKUP_PATH.toAbsolutePath());
            return;
        }
        try {
            Files.copy(DATABASE_PATH, PLAINTEXT_BACKUP_PATH, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new SQLException("Unable to create plaintext database safety backup.", e);
        }
    }

    private static void verifyEncryptedMigration(long expectedNotebooks, long expectedNotes) throws SQLException {
        String url = "jdbc:sqlite:" + ENCRYPTED_TEMP_PATH.toAbsolutePath();
        try (Connection connection = VaultService.openEncryptedConnection(url)) {
            if (tableCount(connection, "notebooks") != expectedNotebooks) {
                throw new SQLException("Encrypted notebook verification failed.");
            }
            if (tableCount(connection, "notes") != expectedNotes) {
                throw new SQLException("Encrypted note verification failed.");
            }
            if (tableCount(connection, "notes_fts") != expectedNotes) {
                throw new SQLException("Encrypted FTS verification failed.");
            }
            verifyDatabaseIntegrity(connection);
        }
    }

    private static void replacePlaintextDatabase() throws SQLException {
        Path temporaryOriginal = APPLICATION_DIRECTORY.resolve("zorza-pre-encryption.db");
        try {
            Files.deleteIfExists(temporaryOriginal);
            Files.move(DATABASE_PATH, temporaryOriginal, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(ENCRYPTED_TEMP_PATH, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(temporaryOriginal, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
                throw e;
            }
            Files.deleteIfExists(temporaryOriginal);
        } catch (IOException e) {
            throw new SQLException("The encrypted database was verified but could not replace the original Zorza database.", e);
        }
    }

    private static void verifyDatabaseIntegrity(
            Connection connection
    ) throws SQLException {

        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                "PRAGMA integrity_check"
                        )
        ) {

            if (!resultSet.next()) {

                throw new SQLException(
                        "SQLite integrity check returned no result."
                );
            }

            String result =
                    resultSet.getString(1);

            if (!"ok".equalsIgnoreCase(result)) {

                throw new SQLException(
                        "SQLite integrity check failed: " +
                                result
                );
            }
        }

        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                "PRAGMA foreign_key_check"
                        )
        ) {

            if (resultSet.next()) {

                throw new SQLException(
                        "Foreign-key integrity check failed."
                );
            }
        }
    }

    private static long tableCount(
            Connection connection,
            String table
    ) throws SQLException {

        /*
         * Table names here are internal constants, not user input.
         */
        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM " + table
                        )
        ) {

            if (!resultSet.next()) {
                throw new SQLException(
                        "Unable to count table: " + table
                );
            }

            return resultSet.getLong(1);
        }
    }

    /*
     * ---------------------------------------------------------
     * APPLICATION DATA DIRECTORY
     * ---------------------------------------------------------
     */

    private static void ensureApplicationDirectory()
            throws SQLException {

        try {

            Files.createDirectories(
                    APPLICATION_DIRECTORY
            );

        } catch (IOException exception) {

            throw new SQLException(
                    "Unable to create Zorza Notes data directory: " +
                            APPLICATION_DIRECTORY,
                    exception
            );
        }
    }

    private static Path determineApplicationDirectory() {

        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ROOT);

        String home =
                System.getProperty("user.home");

        if (os.contains("mac")) {

            return Path.of(
                    home,
                    "Library",
                    "Application Support",
                    "Zorza Notes"
            );
        }

        if (os.contains("win")) {

            String localAppData =
                    System.getenv("LOCALAPPDATA");

            if (
                    localAppData != null &&
                            !localAppData.isBlank()
            ) {

                return Path.of(
                        localAppData,
                        "Zorza Notes"
                );
            }

            return Path.of(
                    home,
                    "AppData",
                    "Local",
                    "Zorza Notes"
            );
        }

        String xdg =
                System.getenv("XDG_DATA_HOME");

        if (
                xdg != null &&
                        !xdg.isBlank()
        ) {

            return Path.of(
                    xdg,
                    "zorza-notes"
            );
        }

        return Path.of(
                home,
                ".local",
                "share",
                "zorza-notes"
        );
    }
}