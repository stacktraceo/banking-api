package ru.bank.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.bank.api.ApiModels.EmailRequest;
import ru.bank.api.ApiModels.PhoneRequest;
import ru.bank.dao.ContactDao.Kind;
import ru.bank.domain.Models.Contact;
import ru.bank.service.AuthService;
import ru.bank.service.ContactService;
import javax.validation.Valid;
import javax.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/api/users/me")
public class ContactController {
    private final ContactService service;
    public ContactController(ContactService service) { this.service = service; }
    @PostMapping("/emails") @ResponseStatus(HttpStatus.CREATED)
    public Contact addEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmailRequest body) {
        return service.add(AuthService.userId(jwt), Kind.EMAIL, body.email);
    }
    @PutMapping("/emails/{id}")
    public Contact updateEmail(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id, @Valid @RequestBody EmailRequest body) {
        return service.update(AuthService.userId(jwt), Kind.EMAIL, id, body.email);
    }
    @DeleteMapping("/emails/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmail(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) { service.delete(AuthService.userId(jwt), Kind.EMAIL, id); }
    @PostMapping("/phones") @ResponseStatus(HttpStatus.CREATED)
    public Contact addPhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhoneRequest body) {
        return service.add(AuthService.userId(jwt), Kind.PHONE, body.phone);
    }
    @PutMapping("/phones/{id}")
    public Contact updatePhone(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id, @Valid @RequestBody PhoneRequest body) {
        return service.update(AuthService.userId(jwt), Kind.PHONE, id, body.phone);
    }
    @DeleteMapping("/phones/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhone(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) { service.delete(AuthService.userId(jwt), Kind.PHONE, id); }
}
