package ru.bank.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.bank.domain.Models.Contact;
import java.util.List;

@Repository
public class ContactDao {
    public enum Kind {
        EMAIL("email_data", "email"), PHONE("phone_data", "phone");
        final String table; final String column;
        Kind(String table, String column) { this.table = table; this.column = column; }
    }
    private final JdbcTemplate jdbc;
    public ContactDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Contact> list(long user, Kind kind) {
        return jdbc.query("SELECT id, " + kind.column + " FROM " + kind.table + " WHERE user_id=? ORDER BY id",
            (rs, n) -> new Contact(rs.getLong(1), rs.getString(2)), user);
    }
    public Contact insert(long user, Kind kind, String value) {
        Long id = jdbc.queryForObject("INSERT INTO " + kind.table + " (user_id," + kind.column + ") VALUES (?,?) RETURNING id", Long.class, user, value);
        return new Contact(id, value);
    }
    public boolean update(long user, Kind kind, long id, String value) {
        return jdbc.update("UPDATE " + kind.table + " SET " + kind.column + "=? WHERE id=? AND user_id=?", value, id, user) == 1;
    }
    public boolean delete(long user, Kind kind, long id) {
        return jdbc.update("DELETE FROM " + kind.table + " WHERE id=? AND user_id=?", id, user) == 1;
    }
}
