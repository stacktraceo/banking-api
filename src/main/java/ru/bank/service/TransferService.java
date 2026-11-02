package ru.bank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.bank.dao.AccountDao;
import ru.bank.dao.TransferDao;
import ru.bank.domain.Models.Account;
import ru.bank.domain.Models.Transfer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    public static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private final AccountDao accounts;
    private final TransferDao transfers;
    public TransferService(AccountDao accounts, TransferDao transfers) { this.accounts = accounts; this.transfers = transfers; }

    @Transactional(timeout = 15)
    public Transfer transfer(long from, long to, BigDecimal value, UUID key) {
        if (from <= 0 || to <= 0 || from == to || key == null || value == null || value.signum() <= 0
                || value.scale() > 2 || value.compareTo(MAX_BALANCE) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", "Use different valid users and a positive amount with at most two decimal places");
        }
        BigDecimal amount = value.setScale(2, RoundingMode.UNNECESSARY);
        // Two explicit statements guarantee the same lock order across opposite transfers.
        Account first = accounts.lock(Math.min(from, to)).orElseThrow(BusinessException::notFound);
        Account second = accounts.lock(Math.max(from, to)).orElseThrow(BusinessException::notFound);
        Account sender = first.userId == from ? first : second;
        Account recipient = first.userId == to ? first : second;
        Optional<Transfer> previous = transfers.find(from, key);
        if (previous.isPresent()) {
            Transfer old = previous.get();
            if (old.toUserId != to || old.value.compareTo(amount) != 0) {
                throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "This key was already used for a different transfer");
            }
            return old;
        }
        if (sender.balance.compareTo(amount) < 0) {
            throw BusinessException.conflict("INSUFFICIENT_FUNDS", "Insufficient funds");
        }
        BigDecimal credited = recipient.balance.add(amount);
        if (credited.compareTo(MAX_BALANCE) > 0) {
            throw BusinessException.conflict("BALANCE_LIMIT", "Recipient balance limit would be exceeded");
        }
        accounts.updateBalance(from, sender.balance.subtract(amount));
        accounts.updateBalance(to, credited);
        Transfer transfer = new Transfer(UUID.randomUUID(), key, from, to, amount, Instant.now());
        transfers.insert(transfer);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { log.info("Transfer committed id={} from={} to={} value={}", transfer.id, from, to, amount); }
            });
        }
        return transfer;
    }
}
