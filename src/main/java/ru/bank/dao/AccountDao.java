package ru.bank.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.bank.domain.Models.Account;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class AccountDao {
    private final JdbcTemplate jdbc;
    public AccountDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Account> lock(long userId) {
        return jdbc.query("SELECT user_id, balance, initial_balance FROM account WHERE user_id=? FOR UPDATE",
            (rs, n) -> new Account(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), userId).stream().findFirst();
    }
    public BigDecimal balance(long userId) {
        return jdbc.queryForObject("SELECT balance FROM account WHERE user_id=?", BigDecimal.class, userId);
    }
    public void updateBalance(long userId, BigDecimal balance) {
        jdbc.update("UPDATE account SET balance=? WHERE user_id=?", balance, userId);
    }
    public boolean claimInterestRun() {
        return jdbc.update("UPDATE interest_state SET last_applied_at=clock_timestamp() "
            + "WHERE id=1 AND last_applied_at <= clock_timestamp() - interval '30 seconds'") == 1;
    }
    public int accrueInterest() {
        // Match transfers' lock order. Keep row locks until this entire transaction commits.
        jdbc.queryForList("SELECT user_id FROM account ORDER BY user_id FOR UPDATE", Long.class);
        return jdbc.update("UPDATE account SET balance=LEAST(round(balance*1.10,2), trunc(initial_balance*2.07,2), 99999999999999999.99) "
            + "WHERE balance < LEAST(trunc(initial_balance*2.07,2), 99999999999999999.99)");
    }
}
