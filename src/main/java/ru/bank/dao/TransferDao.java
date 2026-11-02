package ru.bank.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.bank.domain.Models.Transfer;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferDao {
    private final JdbcTemplate jdbc;
    public TransferDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Transfer> find(long sender, UUID key) {
        return jdbc.query("SELECT * FROM money_transfer WHERE from_user_id=? AND idempotency_key=?",
            (rs,n) -> new Transfer(rs.getObject("id", UUID.class), rs.getObject("idempotency_key", UUID.class),
                rs.getLong("from_user_id"), rs.getLong("to_user_id"), rs.getBigDecimal("value"), rs.getTimestamp("created_at").toInstant()),
            sender, key).stream().findFirst();
    }
    public void insert(Transfer transfer) {
        jdbc.update("INSERT INTO money_transfer (id,idempotency_key,from_user_id,to_user_id,value,created_at) VALUES (?,?,?,?,?,?)",
            transfer.id, transfer.idempotencyKey, transfer.fromUserId, transfer.toUserId, transfer.value, java.sql.Timestamp.from(transfer.createdAt));
    }
}
