package org.zorzanotes;

import org.sqlite.mc.SQLiteMCChacha20Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class EncryptionTest {

    /*
     * IMPORTANT:
     *
     * This is ONLY a disposable encryption test database.
     * It does not touch the real Zorza database.
     */
    private static final Path TEST_DATABASE =
            Path.of("zorza-encryption-test.db");

    /*
     * This password is intentionally hard-coded because this
     * database is disposable.
     *
     * The real Zorza vault will NEVER work this way.
     */
    private static final String TEST_PASSWORD =
            "zorza-test-password-12345";

    private static final String WRONG_PASSWORD =
            "definitely-the-wrong-password";

    private static final String EXPECTED_MESSAGE =
            "Zorza encryption works!";

    public static void main(String[] args) {

        System.out.println();
        System.out.println("==============================");
        System.out.println("ZORZA ENCRYPTION TEST");
        System.out.println("==============================");
        System.out.println();

        try {

            deleteOldTestDatabase();

            System.out.println(
                    "1. Creating encrypted test database..."
            );

            createEncryptedDatabase();

            System.out.println(
                    "   Encrypted database created."
            );

            System.out.println();

            System.out.println(
                    "2. Closing and reopening database..."
            );

            String recovered =
                    readEncryptedDatabase(
                            TEST_PASSWORD
                    );

            System.out.println(
                    "   Database reopened."
            );

            System.out.println();

            System.out.println(
                    "3. Recovered value:"
            );

            System.out.println();

            System.out.println(
                    "   " + recovered
            );

            System.out.println();

            if (!EXPECTED_MESSAGE.equals(recovered)) {

                throw new IllegalStateException(
                        "Recovered data did not match the original data."
                );
            }

            System.out.println(
                    "4. Testing incorrect password..."
            );

            boolean wrongPasswordRejected =
                    testWrongPassword();

            if (!wrongPasswordRejected) {

                throw new IllegalStateException(
                        "SECURITY TEST FAILED: " +
                                "the database could be read with an incorrect password."
                );
            }

            System.out.println(
                    "   Incorrect password rejected."
            );

            System.out.println();
            System.out.println("==============================");
            System.out.println("SUCCESS");
            System.out.println("==============================");
            System.out.println();

            System.out.println(
                    "Zorza successfully created an encrypted database,"
            );

            System.out.println(
                    "closed it, reopened it with the correct password,"
            );

            System.out.println(
                    "recovered the original data, and rejected a wrong password."
            );

            System.out.println();

            System.out.println(
                    "Test database:"
            );

            System.out.println(
                    TEST_DATABASE
                            .toAbsolutePath()
            );

            System.out.println();

            System.out.println(
                    "Your real zorza.db was NOT modified."
            );

            System.out.println();

        } catch (Throwable e) {

            System.err.println();
            System.err.println("==============================");
            System.err.println("ENCRYPTION TEST FAILED");
            System.err.println("==============================");
            System.err.println();

            System.err.println(
                    e.getClass().getName()
            );

            System.err.println();

            System.err.println(
                    e.getMessage()
            );

            System.err.println();

            e.printStackTrace();

            System.err.println();
            System.err.println(
                    "Your real zorza.db was NOT modified."
            );

            System.exit(1);
        }
    }

    // =============================================================
    // DELETE OLD TEST DATABASE
    // =============================================================

    private static void deleteOldTestDatabase()
            throws Exception {

        Files.deleteIfExists(
                TEST_DATABASE
        );

        /*
         * SQLite can potentially leave these beside a database.
         * Remove them too so every test begins completely clean.
         */

        Files.deleteIfExists(
                Path.of(
                        TEST_DATABASE + "-wal"
                )
        );

        Files.deleteIfExists(
                Path.of(
                        TEST_DATABASE + "-shm"
                )
        );
    }

    // =============================================================
    // CREATE ENCRYPTED DATABASE
    // =============================================================

    private static void createEncryptedDatabase()
            throws Exception {

        try (Connection connection =
                     openEncryptedConnection(
                             TEST_PASSWORD
                     );

             Statement statement =
                     connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE encryption_test (
                        id      INTEGER PRIMARY KEY,
                        message TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO encryption_test
                        (
                            id,
                            message
                        )
                    VALUES
                        (
                            1,
                            'Zorza encryption works!'
                        )
                    """);
        }
    }

    // =============================================================
    // READ ENCRYPTED DATABASE
    // =============================================================

    private static String readEncryptedDatabase(
            String password)
            throws Exception {

        try (Connection connection =
                     openEncryptedConnection(
                             password
                     );

             Statement statement =
                     connection.createStatement();

             ResultSet results =
                     statement.executeQuery("""
                             SELECT message
                             FROM encryption_test
                             WHERE id = 1
                             """)) {

            if (!results.next()) {

                throw new IllegalStateException(
                        "The encryption test record was not found."
                );
            }

            return results.getString(
                    "message"
            );
        }
    }

    // =============================================================
    // TEST WRONG PASSWORD
    // =============================================================

    private static boolean testWrongPassword() {

        try (Connection connection =
                     openEncryptedConnection(
                             WRONG_PASSWORD
                     );

             Statement statement =
                     connection.createStatement();

             ResultSet results =
                     statement.executeQuery("""
                             SELECT name
                             FROM sqlite_master
                             WHERE type = 'table'
                             """)) {

            /*
             * Force SQLite to actually read the database.
             *
             * Merely creating a JDBC Connection is not enough
             * to prove that the supplied encryption key works.
             */

            while (results.next()) {

                results.getString(
                        "name"
                );
            }

            return false;

        } catch (Exception expected) {

            return true;
        }
    }

    // =============================================================
    // OPEN ENCRYPTED CONNECTION
    // =============================================================

    private static Connection openEncryptedConnection(
            String password)
            throws Exception {

        String url =
                "jdbc:sqlite:file:" +
                        TEST_DATABASE
                                .toAbsolutePath();

        return SQLiteMCChacha20Config
                .getDefault()
                .withKey(password)
                .build()
                .createConnection(url);
    }
}