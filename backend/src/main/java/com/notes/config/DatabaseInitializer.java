package com.notes.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Configuration
public class DatabaseInitializer {

    @Bean
    CommandLineRunner initializeDatabase(JdbcTemplate jdbcTemplate) {
        return args -> {

            String usersSql = """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        email TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        role TEXT NOT NULL
                            CHECK (role IN ('USER', 'ADMIN')),
                        created_at TEXT NOT NULL
                    )
                    """;

            jdbcTemplate.execute(usersSql);

            String notesSql = """
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        visibility TEXT NOT NULL DEFAULT 'PRIVATE'
                            CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        pdf_storage_name TEXT,
                        original_file_name TEXT,
                        file_size INTEGER,
                        content_type TEXT,
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """;

            jdbcTemplate.execute(notesSql);

            addColumnIfMissing(
                    jdbcTemplate,
                    "visibility",
                    """
                    ALTER TABLE notes
                    ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE'
                    CHECK (visibility IN ('PRIVATE', 'PUBLIC'))
                    """
            );

            addColumnIfMissing(
                    jdbcTemplate,
                    "description",
                    """
                    ALTER TABLE notes
                    ADD COLUMN description TEXT NOT NULL DEFAULT ''
                    """
            );

            addColumnIfMissing(
                    jdbcTemplate,
                    "pdf_storage_name",
                    """
                    ALTER TABLE notes
                    ADD COLUMN pdf_storage_name TEXT
                    """
            );

            addColumnIfMissing(
                    jdbcTemplate,
                    "original_file_name",
                    """
                    ALTER TABLE notes
                    ADD COLUMN original_file_name TEXT
                    """
            );

            addColumnIfMissing(
                    jdbcTemplate,
                    "file_size",
                    """
                    ALTER TABLE notes
                    ADD COLUMN file_size INTEGER
                    """
            );

            addColumnIfMissing(
                    jdbcTemplate,
                    "content_type",
                    """
                    ALTER TABLE notes
                    ADD COLUMN content_type TEXT
                    """
            );

            System.out.println("Users table initialized successfully.");
            System.out.println("Notes table initialized successfully.");
            System.out.println("Note visibility initialized successfully.");
            System.out.println("PDF metadata columns initialized successfully.");
        };
    }

    private void addColumnIfMissing(
            JdbcTemplate jdbcTemplate,
            String columnName,
            String alterSql) {

        List<Map<String, Object>> columns =
                jdbcTemplate.queryForList("PRAGMA table_info(notes)");

        boolean exists = columns.stream()
                .anyMatch(column ->
                        columnName.equalsIgnoreCase(
                                String.valueOf(column.get("name"))
                        )
                );

        if (!exists) {
            jdbcTemplate.execute(alterSql);
        }
    }
}