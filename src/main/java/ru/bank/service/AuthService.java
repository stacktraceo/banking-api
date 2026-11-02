package ru.bank.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.bank.api.ApiModels.TokenResponse;
import ru.bank.dao.UserDao;
import ru.bank.domain.Models.Credentials;
import java.time.Instant;

@Service
public class AuthService {
    private final UserDao users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttl;
    private final String dummyHash;
    public AuthService(UserDao users, PasswordEncoder passwords, JwtEncoder encoder,
            @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.ttl-seconds}") long ttl) {
        this.users = users; this.passwords = passwords; this.encoder = encoder; this.issuer = issuer; this.ttl = ttl;
        this.dummyHash = passwords.encode("dummy-password-for-timing");
    }
    public TokenResponse login(String login, String password) {
        Credentials credentials = users.credentials(ContactService.normalizeEmail(login)).orElse(null);
        boolean matched = passwords.matches(password, credentials == null ? dummyHash : credentials.passwordHash);
        if (credentials == null || !matched) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login or password");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(now.plusSeconds(ttl))
            .claim("USER_ID", credentials.userId).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, ttl);
    }
    public static long userId(Jwt jwt) {
        Object id = jwt.getClaim("USER_ID");
        if (!(id instanceof Number) || ((Number) id).longValue() <= 0
                || ((Number) id).doubleValue() != ((Number) id).longValue()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid USER_ID claim");
        }
        return ((Number) id).longValue();
    }
}
