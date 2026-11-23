package com.cooking.app.repository;

import com.cooking.app.service.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CookingRepository {
    private final JdbcTemplate sql;

    public CookingRepository(JdbcTemplate sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> rows(String statement, Object... args) {
        return sql.queryForList(statement, args);
    }

    public Map<String, Object> one(String statement, Object... args) {
        return rows(statement, args).stream().findFirst()
            .orElseThrow(() -> ApiException.missing("Không tìm thấy dữ liệu"));
    }

    public long id(String statement, Object... args) {
        return sql.queryForObject(statement, Long.class, args);
    }

    public int update(String statement, Object... args) {
        return sql.update(statement, args);
    }

    public void lockUser(long user) {
        id("SELECT user_id FROM Users WITH (UPDLOCK, HOLDLOCK) WHERE user_id=?", user);
    }
}
