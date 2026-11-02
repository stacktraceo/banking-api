package db.demo;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import ru.bank.config.Passwords;
import java.sql.PreparedStatement;

/** Only enabled with the demo profile. */
public class V2__DemoUsers extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        String hash = Passwords.encoder().encode("Password123!");
        try (PreparedStatement user = context.getConnection().prepareStatement("INSERT INTO app_user(id,name,date_of_birth,password) VALUES (?,?,?::date,?)");
             PreparedStatement account = context.getConnection().prepareStatement("INSERT INTO account(user_id,balance,initial_balance) VALUES (?,?,?)");
             PreparedStatement email = context.getConnection().prepareStatement("INSERT INTO email_data(user_id,email) VALUES (?,?)");
             PreparedStatement phone = context.getConnection().prepareStatement("INSERT INTO phone_data(user_id,phone) VALUES (?,?)")) {
            for (int id=1; id<=3; id++) {
                user.setLong(1,id); user.setString(2,new String[]{"Анна Иванова", "Борис Петров", "Вера Соколова"}[id-1]);
                user.setString(3,new String[]{"1993-05-01", "1995-10-12", "2000-02-20"}[id-1]); user.setString(4,hash); user.executeUpdate();
                account.setLong(1,id); account.setBigDecimal(2,new java.math.BigDecimal("1000.00")); account.setBigDecimal(3,new java.math.BigDecimal("1000.00")); account.executeUpdate();
                email.setLong(1,id); email.setString(2,"user"+id+"@example.com"); email.executeUpdate();
                phone.setLong(1,id); phone.setString(2,"7920786543"+id); phone.executeUpdate();
            }
        }
        try (java.sql.Statement statement = context.getConnection().createStatement()) {
            statement.execute("SELECT setval(pg_get_serial_sequence('app_user','id'), (SELECT max(id) FROM app_user))");
        }
    }
}
