package ru.bank.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.bank.api.ApiModels.TransferRequest;
import ru.bank.domain.Models.Transfer;
import ru.bank.service.AuthService;
import ru.bank.service.TransferService;
import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { this.service = service; }
    @PostMapping
    public Transfer transfer(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody TransferRequest request) {
        return service.transfer(AuthService.userId(jwt), request.toUserId, request.value, key);
    }
}
