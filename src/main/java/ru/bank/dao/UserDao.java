package ru.bank.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.bank.domain.Models.Credentials;
import ru.bank.domain.Models.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class UserDao {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public UserDao(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }

    public Optional<User> find(long id) {
        return jdbc.query("SELECT id, name, date_of_birth FROM app_user WHERE id = ?",
            (rs, n) -> new User(rs.getLong("id"), rs.getString("name"), rs.getObject("date_of_birth", LocalDate.class)), id).stream().findFirst();
    }

    public boolean lock(long id) {
        return !jdbc.queryForList("SELECT id FROM app_user WHERE id = ? FOR UPDATE", Long.class, id).isEmpty();
    }

    public Optional<Credentials> credentials(String login) {
        return jdbc.query("SELECT DISTINCT u.id, u.password FROM app_user u "
            + "WHERE EXISTS (SELECT 1 FROM email_data e WHERE e.user_id=u.id AND e.email=?) "
            + "OR EXISTS (SELECT 1 FROM phone_data p WHERE p.user_id=u.id AND p.phone=?)",
            (rs, n) -> new Credentials(rs.getLong("id"), rs.getString("password")), login, login).stream().findFirst();
    }

    public List<User> search(LocalDate date, String phone, String name, String email, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(date, phone, name, email, params);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        return named.query("SELECT u.id, u.name, u.date_of_birth FROM app_user u " + where
            + " ORDER BY u.id LIMIT :limit OFFSET :offset", params,
            (rs, n) -> new User(rs.getLong("id"), rs.getString("name"), rs.getObject("date_of_birth", LocalDate.class)));
    }

    public long count(LocalDate date, String phone, String name, String email) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return named.queryForObject("SELECT count(*) FROM app_user u " + where(date, phone, name, email, params), params, Long.class);
    }

    private String where(LocalDate date, String phone, String name, String email, MapSqlParameterSource params) {
        String sql = "WHERE 1=1";
        if (date != null) { sql += " AND u.date_of_birth > :date"; params.addValue("date", java.sql.Date.valueOf(date)); }
        if (phone != null) { sql += " AND EXISTS (SELECT 1 FROM phone_data p WHERE p.user_id=u.id AND p.phone=:phone)"; params.addValue("phone", phone); }
        if (name != null) {
            sql += " AND u.name LIKE :name ESCAPE '!'";
            params.addValue("name", name.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (email != null) { sql += " AND EXISTS (SELECT 1 FROM email_data e WHERE e.user_id=u.id AND e.email=:email)"; params.addValue("email", email); }
        return sql;
    }
}
