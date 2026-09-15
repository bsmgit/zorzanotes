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

public class NoteRepository {

    // =============================================================
    // FIND NOTES IN NOTEBOOK
    // =============================================================

    public List<Note> findByNotebook(
            long notebookId) {

        List<Note> notes =
                new ArrayList<>();

        String sql = """
                SELECT id,
                       uuid,
                       notebook_id,
                       title,
                       body
                FROM notes
                WHERE notebook_id = ?
                ORDER BY updated_at DESC
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    notebookId
            );

            try (ResultSet results =
                         statement.executeQuery()) {

                while (results.next()) {

                    notes.add(
                            noteFromResultSet(
                                    results
                            )
                    );
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to load notes.",
                    e
            );
        }

        return notes;
    }

    // =============================================================
    // FIND NOTE BY ID
    // =============================================================

    public Note findById(long id) {

        String sql = """
                SELECT id,
                       uuid,
                       notebook_id,
                       title,
                       body
                FROM notes
                WHERE id = ?
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    id
            );

            try (ResultSet results =
                         statement.executeQuery()) {

                if (results.next()) {

                    return noteFromResultSet(
                            results
                    );
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to load note.",
                    e
            );
        }

        return null;
    }

    // =============================================================
    // GLOBAL FTS5 SEARCH
    // =============================================================

    public List<Note> searchAll(
            String query) {

        List<Note> notes =
                new ArrayList<>();

        if (query == null ||
                query.isBlank()) {

            return notes;
        }

        String ftsQuery =
                buildFtsQuery(query);

        if (ftsQuery.isBlank()) {
            return notes;
        }

        String sql = """
                SELECT n.id,
                       n.uuid,
                       n.notebook_id,
                       n.title,
                       n.body
                FROM notes_fts
                JOIN notes n
                    ON n.id = notes_fts.rowid
                WHERE notes_fts MATCH ?
                ORDER BY bm25(notes_fts)
                LIMIT 250
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    ftsQuery
            );

            try (ResultSet results =
                         statement.executeQuery()) {

                while (results.next()) {

                    notes.add(
                            noteFromResultSet(
                                    results
                            )
                    );
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to search notes.",
                    e
            );
        }

        return notes;
    }

    // =============================================================
    // BUILD SAFE FTS QUERY
    // =============================================================

    private String buildFtsQuery(
            String query) {

        /*
         * We intentionally do not send the user's raw text
         * directly to MATCH.
         *
         * FTS5 has its own query language. Characters such as
         * quotes, *, :, parentheses, AND, OR, etc. can otherwise
         * alter the query or cause syntax errors.
         *
         * Instead, ordinary words become quoted prefix terms.
         *
         * Example:
         *
         *     sherlock holm
         *
         * becomes:
         *
         *     "sherlock"* AND "holm"*
         *
         * This gives us useful search-as-you-type behavior.
         */

        String cleaned =
                query
                        .trim()
                        .replaceAll(
                                "[^\\p{L}\\p{N}_]+",
                                " "
                        );

        if (cleaned.isBlank()) {
            return "";
        }

        String[] words =
                cleaned.split("\\s+");

        StringBuilder result =
                new StringBuilder();

        for (String word : words) {

            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(" AND ");
            }

            String escaped =
                    word.replace(
                            "\"",
                            "\"\""
                    );

            result.append("\"")
                    .append(escaped)
                    .append("\"*");
        }

        return result.toString();
    }

    // =============================================================
    // CREATE EMPTY NOTE
    // =============================================================

    public Note create(
            long notebookId) {

        return create(
                notebookId,
                "Untitled Note",
                ""
        );
    }

    // =============================================================
    // CREATE NOTE
    // =============================================================

    public Note create(
            long notebookId,
            String title,
            String body) {

        String uuid =
                UUID.randomUUID()
                        .toString();

        String now =
                Instant.now()
                        .toString();

        String safeTitle =
                title == null ||
                        title.isBlank()
                        ? "Untitled Note"
                        : title.trim();

        String safeBody =
                body == null
                        ? ""
                        : body;

        String sql = """
                INSERT INTO notes
                    (
                        uuid,
                        notebook_id,
                        title,
                        body,
                        created_at,
                        updated_at
                    )
                VALUES
                    (?, ?, ?, ?, ?, ?)
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

            statement.setLong(
                    2,
                    notebookId
            );

            statement.setString(
                    3,
                    safeTitle
            );

            statement.setString(
                    4,
                    safeBody
            );

            statement.setString(
                    5,
                    now
            );

            statement.setString(
                    6,
                    now
            );

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (keys.next()) {

                    return new Note(
                            keys.getLong(1),
                            uuid,
                            notebookId,
                            safeTitle,
                            safeBody
                    );
                }
            }

            throw new SQLException(
                    "No ID returned for note."
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to create note.",
                    e
            );
        }
    }

    // =============================================================
    // UPDATE NOTE
    // =============================================================

    public void update(
            Note note) {

        String sql = """
                UPDATE notes
                SET title = ?,
                    body = ?,
                    updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    note.getTitle()
            );

            statement.setString(
                    2,
                    note.getBody()
            );

            statement.setString(
                    3,
                    Instant.now()
                            .toString()
            );

            statement.setLong(
                    4,
                    note.getId()
            );

            statement.executeUpdate();

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to save note.",
                    e
            );
        }
    }

    // =============================================================
    // DELETE NOTE
    // =============================================================

    public void delete(
            Note note) {

        String sql = """
                DELETE FROM notes
                WHERE id = ?
                """;

        try (Connection connection =
                     Database.connect();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    note.getId()
            );

            statement.executeUpdate();

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Unable to delete note.",
                    e
            );
        }
    }

    // =============================================================
    // RESULT SET -> NOTE
    // =============================================================

    private Note noteFromResultSet(
            ResultSet results)
            throws SQLException {

        return new Note(
                results.getLong("id"),
                results.getString("uuid"),
                results.getLong(
                        "notebook_id"
                ),
                results.getString(
                        "title"
                ),
                results.getString(
                        "body"
                )
        );
    }
}