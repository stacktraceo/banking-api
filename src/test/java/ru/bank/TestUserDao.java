package ru.bank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Creates complete fixtures through DAO, without exposing a user creation API. */
public class TestUserDao {
    private final JdbcTemplate jdbc;
    private final String passwordHash;
    public TestUserDao(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwordHash = passwords.encode("Password123!"); }
    @Transactional public void create(long id, String name, String birth, String balance) {
        jdbc.update("INSERT INTO app_user(id,name,date_of_birth,password) VALUES (?,?,?::date,?)",id,name,birth,passwordHash);
        jdbc.update("INSERT INTO account(user_id,balance,initial_balance) VALUES (?,?,?)",id,new BigDecimal(balance),new BigDecimal(balance));
        jdbc.update("INSERT INTO email_data(user_id,email) VALUES (?,?)",id,"user"+id+"@example.com");
        jdbc.update("INSERT INTO phone_data(user_id,phone) VALUES (?,?)",id,"7920786543"+id);
    }
}
