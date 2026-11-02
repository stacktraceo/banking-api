package ru.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.bank.dao.AccountDao;
import ru.bank.service.BusinessException;
import ru.bank.service.ContactService;
import ru.bank.dao.ContactDao.Kind;
import ru.bank.service.InterestService;
import ru.bank.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties={"app.interest.enabled=false", "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "spring.datasource.password=test"})
@AutoConfigureMockMvc
@Import(BankingApiIT.Fixtures.class)
class BankingApiIT {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry registry) {
        // Default path always uses Testcontainers; external PostgreSQL is an explicit fallback.
        String external = System.getProperty("it.db.url");
        if (external == null) {
            postgres.start();
            registry.add("spring.datasource.url",postgres::getJdbcUrl);
            registry.add("spring.datasource.username",postgres::getUsername);
            registry.add("spring.datasource.password",postgres::getPassword);
        } else {
            registry.add("spring.datasource.url",() -> external);
            registry.add("spring.datasource.username",() -> System.getProperty("it.db.user","postgres"));
            registry.add("spring.datasource.password",() -> System.getProperty("it.db.password","test"));
        }
    }
    @AfterAll static void stopDatabase() { if (postgres.isRunning()) { postgres.stop(); } }
    @TestConfiguration static class Fixtures {
        @Bean TestUserDao testUserDao(JdbcTemplate jdbc, PasswordEncoder passwords) { return new TestUserDao(jdbc,passwords); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestUserDao fixtures;
    @Autowired TransferService transfers;
    @Autowired AccountDao accounts;
    @Autowired InterestService interest;
    @Autowired ContactService contacts;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired PlatformTransactionManager transactionManager;
    String token;

    @BeforeEach void reset() throws Exception {
        jdbc.execute("TRUNCATE money_transfer, email_data, phone_data, account, app_user RESTART IDENTITY CASCADE");
        fixtures.create(1,"Anna","1993-05-01","100.00");
        fixtures.create(2,"Boris","1995-10-12","100.00");
        fixtures.create(3,"Annabelle","2000-02-20","100.00");
        jdbc.update("UPDATE interest_state SET last_applied_at=clock_timestamp()");
        token = login("user1@example.com");
    }
    private String login(String login) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(APPLICATION_JSON).content("{\"login\":\""+login+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
    private String bearer() { return "Bearer " + token; }
    private void due() { jdbc.update("UPDATE interest_state SET last_applied_at=clock_timestamp()-interval '31 seconds'"); }
    private long transferCount() { return jdbc.queryForObject("SELECT count(*) FROM money_transfer", Long.class); }

    @Test void authenticatesByEmailAndPhoneAndDoesNotExposePassword() throws Exception {
        login("79207865431");
        mvc.perform(get("/api/users/me").header("Authorization",bearer())).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(1)).andExpect(jsonPath("$.balance").value(100.00))
            .andExpect(jsonPath("$.user.password").doesNotExist());
        mvc.perform(post("/api/auth/token").contentType(APPLICATION_JSON).content("{\"login\":\"user1@example.com\",\"password\":\"wrongpass\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
    @Test void filtersAreCombinedAndDateIsStrictAndPaginationStable() throws Exception {
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("name","Anna").param("dateOfBirth","01.05.1993").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(3));
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("phone","79207865432").param("email","USER2@EXAMPLE.COM"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(2));
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("page","1").param("size","1"))
            .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content[0].id").value(2));
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("name","%"))
            .andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void validatesQueryAndJsonAndRejectsUnknownProperties() throws Exception {
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users").header("Authorization",bearer()).param("dateOfBirth","not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/me/phones").header("Authorization",bearer()).contentType(APPLICATION_JSON).content("{\"phone\":\"+79207865431\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers").header("Authorization",bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(APPLICATION_JSON)
            .content("{\"toUserId\":2,\"value\":1,\"fromUserId\":3}")).andExpect(status().isBadRequest());
    }
    @Test void addsUpdatesAndDeletesOnlyOwnContacts() throws Exception {
        String created = mvc.perform(post("/api/users/me/emails").header("Authorization",bearer()).contentType(APPLICATION_JSON).content("{\"email\":\"NEW@EXAMPLE.COM\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.value").value("new@example.com")).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();
        mvc.perform(put("/api/users/me/emails/"+id).header("Authorization",bearer()).contentType(APPLICATION_JSON).content("{\"email\":\"updated@example.com\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/users/me/emails/"+id).header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/me/emails/1").header("Authorization",bearer())).andExpect(status().isConflict());
        mvc.perform(put("/api/users/me/emails/2").header("Authorization",bearer()).contentType(APPLICATION_JSON).content("{\"email\":\"stolen@example.com\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/me/phones/2").header("Authorization",bearer())).andExpect(status().isNotFound());
        mvc.perform(post("/api/users/me/emails").header("Authorization",bearer()).contentType(APPLICATION_JSON).content("{\"email\":\"USER2@EXAMPLE.COM\"}"))
            .andExpect(status().isConflict());
    }
    @Test void transferUsesJwtSenderAndRetriesReturnSameTransfer() throws Exception {
        UUID key = UUID.randomUUID();
        String first = mvc.perform(post("/api/transfers").header("Authorization",bearer()).header("Idempotency-Key",key).contentType(APPLICATION_JSON)
            .content("{\"toUserId\":2,\"value\":12.34}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fromUserId").value(1)).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/transfers").header("Authorization",bearer()).header("Idempotency-Key",key).contentType(APPLICATION_JSON)
            .content("{\"toUserId\":2,\"value\":12.34}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(first).get("id")).isEqualTo(mapper.readTree(second).get("id"));
        assertThat(accounts.balance(1)).isEqualByComparingTo("87.66");
        assertThat(accounts.balance(2)).isEqualByComparingTo("112.34");
        assertThat(transferCount()).isEqualTo(1);
        mvc.perform(post("/api/transfers").header("Authorization",bearer()).header("Idempotency-Key",key).contentType(APPLICATION_JSON)
            .content("{\"toUserId\":3,\"value\":12.34}"))
            .andExpect(status().isConflict());
    }
    @Test void rejectsInvalidTransfersWithoutChangingBalances() throws Exception {
        for (String amount : List.of("0", "-1", "0.001", "100000000000000000.00")) {
            mvc.perform(post("/api/transfers").header("Authorization",bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(APPLICATION_JSON)
                .content("{\"toUserId\":2,\"value\":"+amount+"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/transfers").header("Authorization",bearer()).contentType(APPLICATION_JSON)
            .content("{\"toUserId\":2,\"value\":1}")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> transfers.transfer(1,999,BigDecimal.ONE,UUID.randomUUID())).isInstanceOf(BusinessException.class);
        assertThat(accounts.balance(1)).isEqualByComparingTo("100.00");
        assertThat(transferCount()).isZero();
    }
    @Test void parallelSpendingNeverOverdrawsAndConservesMoney() throws Exception {
        AtomicInteger succeeded = new AtomicInteger();
        parallel(20, index -> {
            try { transfers.transfer(1,2,BigDecimal.TEN,UUID.randomUUID()); succeeded.incrementAndGet(); }
            catch (BusinessException e) { assertThat(e.code).isEqualTo("INSUFFICIENT_FUNDS"); }
        });
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(accounts.balance(1)).isEqualByComparingTo("0.00");
        assertThat(accounts.balance(2)).isEqualByComparingTo("200.00");
        assertThat(transferCount()).isEqualTo(10);
    }
    @Test void oppositeTransfersHaveNoDeadlockOrLostUpdates() throws Exception {
        parallel(40,index -> transfers.transfer(index%2==0?1:2,index%2==0?2:1,BigDecimal.ONE,UUID.randomUUID()));
        assertThat(accounts.balance(1)).isEqualByComparingTo("100.00");
        assertThat(accounts.balance(2)).isEqualByComparingTo("100.00");
        assertThat(transferCount()).isEqualTo(40);
    }
    @Test void concurrentDuplicateRequestsAreAppliedOnce() throws Exception {
        UUID key = UUID.randomUUID();
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        parallel(16,index -> ids.add(transfers.transfer(1,2,BigDecimal.TEN,key).id));
        assertThat(ids).hasSize(1);
        assertThat(transferCount()).isEqualTo(1);
        assertThat(accounts.balance(1)).isEqualByComparingTo("90.00");
    }
    @Test void recordFailureRollsBackBothBalanceChanges() {
        jdbc.execute("CREATE FUNCTION reject_test_transfer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_test_transfer BEFORE INSERT ON money_transfer FOR EACH ROW EXECUTE FUNCTION reject_test_transfer()");
        try {
            assertThatThrownBy(() -> transfers.transfer(1,2,BigDecimal.ONE,UUID.randomUUID())).isInstanceOf(RuntimeException.class);
            assertThat(accounts.balance(1)).isEqualByComparingTo("100.00");
            assertThat(accounts.balance(2)).isEqualByComparingTo("100.00");
            assertThat(transferCount()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_transfer ON money_transfer");
            jdbc.execute("DROP FUNCTION reject_test_transfer()");
        }
    }
    @Test void interestCompoundsAndStopsExactlyAt207Percent() {
        due(); interest.accrue();
        assertThat(accounts.balance(1)).isEqualByComparingTo("110.00");
        due(); interest.accrue();
        assertThat(accounts.balance(1)).isEqualByComparingTo("121.00");
        for (int n=0;n<15;n++) { due(); interest.accrue(); }
        assertThat(accounts.balance(1)).isEqualByComparingTo("207.00");
        assertThat(interest.accrue()).isZero();
    }
    @Test void concurrentInterestRunnersApplyOnlyOneTick() throws Exception {
        due();
        parallel(8,index -> interest.accrue());
        assertThat(accounts.balance(1)).isEqualByComparingTo("110.00");
    }
    @Test void interestDoesNotReduceBalanceAboveCapAndZeroDepositIsSupported() {
        jdbc.update("UPDATE account SET balance=300 WHERE user_id=1");
        fixtures.create(4,"Zero","1990-01-01","0.00");
        due(); interest.accrue();
        assertThat(accounts.balance(1)).isEqualByComparingTo("300.00");
        assertThat(accounts.balance(4)).isEqualByComparingTo("0.00");
    }
    @Test void fractionalCapRoundsDownSoItNeverExceeds207Percent() {
        fixtures.create(4,"Small","1990-01-01","0.99");
        for (int n=0;n<20;n++) { due(); interest.accrue(); }
        assertThat(accounts.balance(4)).isEqualByComparingTo("2.04");
    }
    @Test void maximumDepositDoesNotOverflowOrBlockInterestForOtherUsers() {
        fixtures.create(4,"Maximum","1990-01-01","99999999999999999.99");
        due(); interest.accrue();
        assertThat(accounts.balance(4)).isEqualByComparingTo("99999999999999999.99");
        assertThat(accounts.balance(1)).isEqualByComparingTo("110.00");
    }
    @Test void simultaneousDeletesKeepOneContact() throws Exception {
        long added = contacts.add(1,Kind.EMAIL,"second@example.com").id;
        AtomicInteger succeeded = new AtomicInteger();
        parallel(2,index -> {
            try { contacts.delete(1,Kind.EMAIL,index==0?1:added); succeeded.incrementAndGet(); }
            catch (BusinessException e) { assertThat(e.code).isEqualTo("LAST_CONTACT"); }
        });
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_data WHERE user_id=1",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentInterestAndTransfersDoNotLoseUpdates() throws Exception {
        due();
        parallel(41,index -> {
            if (index==0) { interest.accrue(); }
            else { transfers.transfer(index%2==0?1:2,index%2==0?2:1,BigDecimal.ONE,UUID.randomUUID()); }
        });
        assertThat(accounts.balance(1).add(accounts.balance(2))).isEqualByComparingTo("220.00");
        assertThat(transferCount()).isEqualTo(40);
    }
    @Test void databaseProtectsNonnegativeBalancesAndCompleteUser() {
        assertThatThrownBy(() -> jdbc.update("UPDATE account SET balance=-1 WHERE user_id=1")).isInstanceOf(RuntimeException.class);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> { jdbc.update("DELETE FROM email_data WHERE user_id=1"); return null; })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_data WHERE user_id=1",Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE account SET initial_balance=0 WHERE user_id=1")).isInstanceOf(RuntimeException.class);
    }
    @Test void expiredAndTamperedTokensAreRejected() throws Exception {
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("banking-api").issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).claim("USER_ID",1).build())).getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        String[] parts = token.split("\\.");
        String bad = parts[0]+"."+parts[1]+"."+(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+bad)).andExpect(status().isUnauthorized());
    }
    @Test void swaggerIsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/transfers']").exists());
    }
    private void parallel(int count, java.util.function.IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int n=0;n<count;n++) { final int i=n; futures.add(pool.submit(() -> { start.await(); action.accept(i); return null; })); }
            start.countDown();
            for (Future<?> future : futures) { future.get(45,TimeUnit.SECONDS); }
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
    }
}
