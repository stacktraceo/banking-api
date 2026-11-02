package ru.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Models {
    private Models() { }

    public static final class User {
        public final long id;
        public final String name;
        public final LocalDate dateOfBirth;
        public User(long id, String name, LocalDate dateOfBirth) {
            this.id = id; this.name = name; this.dateOfBirth = dateOfBirth;
        }
    }

    public static final class Credentials {
        public final long userId;
        public final String passwordHash;
        public Credentials(long userId, String passwordHash) {
            this.userId = userId; this.passwordHash = passwordHash;
        }
    }

    public static final class Account {
        public final long userId;
        public final BigDecimal balance;
        public final BigDecimal initialBalance;
        public Account(long userId, BigDecimal balance, BigDecimal initialBalance) {
            this.userId = userId; this.balance = balance; this.initialBalance = initialBalance;
        }
    }

    public static final class Contact {
        public final long id;
        public final String value;
        public Contact(long id, String value) { this.id = id; this.value = value; }
    }

    public static final class Transfer {
        public final UUID id;
        public final UUID idempotencyKey;
        public final long fromUserId;
        public final long toUserId;
        public final BigDecimal value;
        public final Instant createdAt;
        public Transfer(UUID id, UUID key, long from, long to, BigDecimal value, Instant createdAt) {
            this.id = id; this.idempotencyKey = key; this.fromUserId = from;
            this.toUserId = to; this.value = value; this.createdAt = createdAt;
        }
    }
}
