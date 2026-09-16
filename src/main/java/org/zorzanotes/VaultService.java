package org.zorzanotes;

import org.sqlite.mc.SQLiteMCChacha20Config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

public final class VaultService {

    private static char[] password;

    private VaultService() {
    }

    public static synchronized void unlock(char[] suppliedPassword) {

        clearPassword();

        if (suppliedPassword == null || suppliedPassword.length == 0) {
            throw new IllegalArgumentException(
                    "The Zorza vault password cannot be empty."
            );
        }

        password = Arrays.copyOf(
                suppliedPassword,
                suppliedPassword.length
        );
    }

    public static synchronized boolean isUnlocked() {
        return password != null && password.length > 0;
    }

    public static synchronized Connection openEncryptedConnection(
            String jdbcUrl) throws SQLException {

        if (!isUnlocked()) {
            throw new SQLException(
                    "The Zorza Notes vault has not been unlocked."
            );
        }

        String key = new String(password);

        Connection connection = null;

        try {

            connection = SQLiteMCChacha20Config
                    .getDefault()
                    .withKey(key)
                    .build()
                    .createConnection(jdbcUrl);

            verifyConnection(connection);

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            return connection;

        } catch (SQLException exception) {

            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }

            throw exception;
        }
    }

    public static void verifyConnection(
            Connection connection) throws SQLException {

        try (
                Statement statement = connection.createStatement();

                ResultSet resultSet = statement.executeQuery(
                        "SELECT count(*) FROM sqlite_master"
                )
        ) {

            if (!resultSet.next()) {
                throw new SQLException(
                        "Unable to verify the encrypted Zorza database."
                );
            }
        }
    }

    public static synchronized void clearPassword() {

        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }
}