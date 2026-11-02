package ru.bank.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;
import ru.bank.api.ApiModels.LoginRequest;
import ru.bank.api.ApiModels.TokenResponse;
import ru.bank.service.AuthService;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/token") @SecurityRequirements
    public TokenResponse token(@Valid @RequestBody LoginRequest request) { return auth.login(request.login, request.password); }
}
