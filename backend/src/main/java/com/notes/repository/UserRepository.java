package com.notes.repository;

import com.notes.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User findByUsername(String username) {
        String sql = """
                SELECT id, username, email, password_hash, role, created_at
                FROM users
                WHERE username = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new User(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role"),
                        resultSet.getString("created_at")
                ),
                username
        );

        return users.isEmpty() ? null : users.get(0);
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?",
            Integer.class,
            username
        );

        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Integer.class,
            email
        );

        return count != null && count > 0;
    }

    public void save(User user) {
        String sql = """
            INSERT INTO users (username, email, password_hash, role, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(
            sql,
            user.getUsername(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getRole(),
            user.getCreatedAt()
        );
    }

public User findByEmail(String email) {
    String sql = """
            SELECT id, username, email, password_hash, role, created_at
            FROM users
            WHERE email = ?
            """;

    List<User> users = jdbcTemplate.query(
            sql,
            (resultSet, rowNum) -> new User(
                    resultSet.getLong("id"),
                    resultSet.getString("username"),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("role"),
                    resultSet.getString("created_at")
            ),
            email
    );

    return users.isEmpty() ? null : users.get(0);
}

public boolean hasAdmin() {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'",
        Integer.class
    );

    return count != null && count > 0;
}


}
