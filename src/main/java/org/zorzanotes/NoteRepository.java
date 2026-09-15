package org.zorzanotes;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NoteRepository {

    public List<Note> findByNotebook(long notebookId) {

        List<Note> notes = new ArrayList<>();

        String sql = """
                SELECT id, uuid, notebook_id, title, body
                FROM notes
                WHERE notebook_id = ?
                ORDER BY updated_at DESC
                """;

        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, notebookId);

            try (ResultSet results = statement.executeQuery()) {

                while (results.next()) {

                    notes.add(new Note(
                            results.getLong("id"),
                            results.getString("uuid"),
                            results.getLong("notebook_id"),
                            results.getString("title"),
                            results.getString("body")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Unable to load notes.", e);
        }

        return notes;
    }

    public Note create(long notebookId) {

        String uuid = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        String sql = """
                INSERT INTO notes
                    (uuid, notebook_id, title, body, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS
             )) {

            statement.setString(1, uuid);
            statement.setLong(2, notebookId);
            statement.setString(3, "Untitled Note");
            statement.setString(4, "");
            statement.setString(5, now);
            statement.setString(6, now);

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {

                if (keys.next()) {
                    return new Note(
                            keys.getLong(1),
                            uuid,
                            notebookId,
                            "Untitled Note",
                            ""
                    );
                }
            }

            throw new SQLException("No ID returned for note.");

        } catch (SQLException e) {
            throw new RuntimeException("Unable to create note.", e);
        }
    }

    public void update(Note note) {

        String sql = """
                UPDATE notes
                SET title = ?,
                    body = ?,
                    updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, note.getTitle());
            statement.setString(2, note.getBody());
            statement.setString(3, Instant.now().toString());
            statement.setLong(4, note.getId());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Unable to save note.", e);
        }
    }
}