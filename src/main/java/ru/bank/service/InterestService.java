package ru.bank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bank.dao.AccountDao;

@Service
public class InterestService {
    private static final Logger log = LoggerFactory.getLogger(InterestService.class);
    private final AccountDao accounts;
    public InterestService(AccountDao accounts) { this.accounts = accounts; }
    @Transactional(timeout = 25)
    public int accrue() {
        if (!accounts.claimInterestRun()) { return 0; }
        int changed = accounts.accrueInterest();
        log.info("Interest run updated {} accounts", changed);
        return changed;
    }
}
