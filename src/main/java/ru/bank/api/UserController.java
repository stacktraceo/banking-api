package ru.bank.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.bank.api.ApiModels.*;
import ru.bank.service.AuthService;
import ru.bank.service.UserService;
import javax.validation.constraints.*;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @GetMapping("/me") public MeResponse me(@AuthenticationPrincipal Jwt jwt) { return users.me(AuthService.userId(jwt)); }
    @GetMapping("/{id}") public UserResponse get(@PathVariable @Positive long id) { return users.get(id); }
    @GetMapping public UserPage search(
            @RequestParam(required=false) @DateTimeFormat(pattern="dd.MM.yyyy") LocalDate dateOfBirth,
            @RequestParam(required=false) @Pattern(regexp="[0-9]{11,13}") String phone,
            @RequestParam(required=false) @Size(min=1, max=500) String name,
            @RequestParam(required=false) @Email @Size(max=200) String email,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return users.search(dateOfBirth, phone, name, email, page, size);
    }
}
