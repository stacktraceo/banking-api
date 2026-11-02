package ru.bank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return Passwords.encoder(); }
    @Bean public SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] decoded = Base64.getDecoder().decode(secret);
        if (decoded.length < 32) { throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes encoded as Base64"); }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }
    @Bean public JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean public JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
            .antMatchers("/api/auth/token", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .anyRequest().authenticated().and()
            .oauth2ResourceServer().jwt().and()
            .authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), Map.of("code", "UNAUTHORIZED", "message", "Authentication required"));
            }).and()
            .exceptionHandling().accessDeniedHandler((request, response, ex) -> {
                response.setStatus(403); response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), Map.of("code", "FORBIDDEN", "message", "Access denied"));
            }).and().build();
    }
}
