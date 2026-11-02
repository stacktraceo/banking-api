package ru.bank.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

public final class Passwords {
    private Passwords() { }
    public static PasswordEncoder encoder() {
        // PBKDF2 accepts the full required 8..500 characters, including Unicode.
        return new Pbkdf2PasswordEncoder("", 310000, 256);
    }
}
