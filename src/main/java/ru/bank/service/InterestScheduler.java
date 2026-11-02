package ru.bank.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true", matchIfMissing = true)
public class InterestScheduler {
    private final InterestService service;
    public InterestScheduler(InterestService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${app.interest.delay-ms:30000}", initialDelayString = "${app.interest.delay-ms:30000}")
    public void tick() { service.accrue(); }
}
