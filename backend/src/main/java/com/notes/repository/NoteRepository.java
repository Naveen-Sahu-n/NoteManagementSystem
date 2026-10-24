package com.notes.repository;

import com.notes.model.Note;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class NoteRepository {

    private final JdbcTemplate jdbcTemplate;

    public NoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private Note mapRow(
            java.sql.ResultSet rs,
            int rowNum) throws java.sql.SQLException {

        Note note = new Note(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("visibility"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );

        note.setDescription(rs.getString("description"));
        note.setPdfStorageName(rs.getString("pdf_storage_name"));
        note.setOriginalFileName(rs.getString("original_file_name"));

        long fileSize = rs.getLong("file_size");
        note.setFileSize(rs.wasNull() ? null : fileSize);

        note.setContentType(rs.getString("content_type"));

        return note;
    }

    private String selectColumns() {
        return """
                id, user_id, title, content, visibility,
                created_at, updated_at, description,
                pdf_storage_name, original_file_name,
                file_size, content_type
                """;
    }

    public Note save(Note note) {

        String sql = """
                INSERT INTO notes (
                    user_id, title, content, visibility,
                    created_at, updated_at, description,
                    pdf_storage_name, original_file_name,
                    file_size, content_type
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            Statement.RETURN_GENERATED_KEYS
                    );

            statement.setLong(1, note.getUserId());
            statement.setString(2, note.getTitle());
            statement.setString(3, note.getContent());
            statement.setString(4, note.getVisibility());
            statement.setString(5, note.getCreatedAt());
            statement.setString(6, note.getUpdatedAt());
            statement.setString(
                    7,
                    note.getDescription() == null
                            ? "" : note.getDescription()
            );
            statement.setString(8, note.getPdfStorageName());
            statement.setString(9, note.getOriginalFileName());

            if (note.getFileSize() == null) {
                statement.setNull(
                        10, java.sql.Types.BIGINT
                );
            } else {
                statement.setLong(10, note.getFileSize());
            }

            statement.setString(11, note.getContentType());

            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (generatedId != null) {
            return findByIdAndUserId(
                    generatedId.longValue(),
                    note.getUserId()
            );
        }

        return jdbcTemplate.execute(
                (Connection connection) -> {
                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "SELECT last_insert_rowid()")) {

                        try (java.sql.ResultSet rs =
                                     statement.executeQuery()) {

                            if (!rs.next()) {
                                throw new IllegalStateException(
                                        "Could not retrieve inserted note ID."
                                );
                            }

                            long id = rs.getLong(1);

                            return findByIdAndUserId(
                                    id, note.getUserId()
                            );
                        }
                    }
                }
        );
    }

    public List<Note> findVisibleToUser(Long userId) {

        String sql = """
                SELECT %s
                FROM notes
                WHERE user_id = ? OR visibility = 'PUBLIC'
                ORDER BY id DESC
                """.formatted(selectColumns());

        return jdbcTemplate.query(
                sql, this::mapRow, userId
        );
    }

    public List<Note> findByUserId(Long userId) {

        String sql = """
                SELECT %s
                FROM notes
                WHERE user_id = ?
                ORDER BY id DESC
                """.formatted(selectColumns());

        return jdbcTemplate.query(
                sql, this::mapRow, userId
        );
    }

    public Note findByIdAndUserId(Long id, Long userId) {

        String sql = """
                SELECT %s
                FROM notes
                WHERE id = ? AND user_id = ?
                """.formatted(selectColumns());

        List<Note> notes = jdbcTemplate.query(
                sql, this::mapRow, id, userId
        );

        return notes.isEmpty() ? null : notes.get(0);
    }

    public Note findAccessibleById(Long id, Long userId) {

        String sql = """
                SELECT %s
                FROM notes
                WHERE id = ?
                  AND (user_id = ? OR visibility = 'PUBLIC')
                """.formatted(selectColumns());

        List<Note> notes = jdbcTemplate.query(
                sql, this::mapRow, id, userId
        );

        return notes.isEmpty() ? null : notes.get(0);
    }

    public boolean update(
            Long id,
            Long userId,
            String title,
            String content,
            String updatedAt) {

        String sql = """
                UPDATE notes
                SET title = ?, content = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql, title, content, updatedAt, id, userId
        ) > 0;
    }

    public boolean updateVisibility(
            Long id,
            Long userId,
            String visibility,
            String updatedAt) {

        String sql = """
                UPDATE notes
                SET visibility = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql, visibility, updatedAt, id, userId
        ) > 0;
    }

    public boolean clearPdfAttachment(
            Long id,
            Long userId,
            String updatedAt) {

        String sql = """
                UPDATE notes
                SET pdf_storage_name = NULL,
                    original_file_name = NULL,
                    file_size = NULL,
                    content_type = NULL,
                    updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql, updatedAt, id, userId
        ) > 0;
    }

    public boolean delete(Long id, Long userId) {

        String sql = """
                DELETE FROM notes
                WHERE id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql, id, userId
        ) > 0;
    }
}