package ru.bank.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.bank.domain.Models.Contact;
import ru.bank.domain.Models.User;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {
    private ApiModels() { }
    public static final class LoginRequest {
        @NotBlank @Size(max=200) public String login;
        @NotBlank @Size(min=8, max=500) public String password;
    }
    public static final class EmailRequest {
        @NotBlank @Email @Size(max=200) public String email;
    }
    public static final class PhoneRequest {
        @NotBlank @Pattern(regexp="[0-9]{11,13}") public String phone;
    }
    public static final class TransferRequest {
        @NotNull @Positive public Long toUserId;
        @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) public BigDecimal value;
    }
    public static final class TokenResponse {
        public final String accessToken;
        public final String tokenType = "Bearer";
        public final long expiresIn;
        public TokenResponse(String token, long ttl) { this.accessToken = token; this.expiresIn = ttl; }
    }
    public static final class UserResponse {
        public final long id;
        public final String name;
        @JsonFormat(pattern="dd.MM.yyyy") public final LocalDate dateOfBirth;
        public final List<Contact> emails;
        public final List<Contact> phones;
        public UserResponse(User user, List<Contact> emails, List<Contact> phones) {
            this.id = user.id; this.name = user.name; this.dateOfBirth = user.dateOfBirth;
            this.emails = emails; this.phones = phones;
        }
    }
    public static final class MeResponse {
        public final UserResponse user;
        public final BigDecimal balance;
        public MeResponse(UserResponse user, BigDecimal balance) { this.user = user; this.balance = balance; }
    }
    public static final class UserPage {
        public final List<UserResponse> content;
        public final long totalElements;
        public final long totalPages;
        public final int page;
        public final int size;
        public UserPage(List<UserResponse> content, long total, int page, int size) {
            this.content = content; this.totalElements = total; this.page = page; this.size = size;
            this.totalPages = (total + size - 1) / size;
        }
    }
}
