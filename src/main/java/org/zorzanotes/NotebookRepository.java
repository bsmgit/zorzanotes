package org.zorzanotes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotebookRepository {

    public List<Notebook> findAll() {

        List<Notebook> notebooks =
                new ArrayList<>();

        String sql = """
                SELECT id, uuid, name
                FROM notebooks
                ORDER BY name
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet results =
                     statement.executeQuery()) {

            while (results.next()) {

                notebooks.add(
                        new Notebook(
                                results.getLong("id"),
                                results.getString("uuid"),
                                results.getString("name")
                        )
                );
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to load notebooks.",
                    e
            );
        }

        return notebooks;
    }

    public Notebook create(
            String name) {

        String uuid =
                UUID.randomUUID()
                        .toString();

        String now =
                Instant.now()
                        .toString();

        String sql = """
                INSERT INTO notebooks
                    (
                        uuid,
                        name,
                        created_at,
                        updated_at
                    )
                VALUES
                    (?, ?, ?, ?)
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(
                    1,
                    uuid
            );

            statement.setString(
                    2,
                    name
            );

            statement.setString(
                    3,
                    now
            );

            statement.setString(
                    4,
                    now
            );

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (keys.next()) {

                    return new Notebook(
                            keys.getLong(1),
                            uuid,
                            name
                    );
                }
            }

            throw new SQLException(
                    "No ID returned for notebook."
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to create notebook.",
                    e
            );
        }
    }

    public void delete(
            Notebook notebook) {

        String sql = """
                DELETE FROM notebooks
                WHERE id = ?
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    notebook.getId()
            );

            statement.executeUpdate();

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to delete notebook.",
                    e
            );
        }
    }
}