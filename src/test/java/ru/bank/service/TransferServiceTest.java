package ru.bank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.bank.dao.AccountDao;
import ru.bank.dao.TransferDao;
import ru.bank.domain.Models.Account;
import ru.bank.domain.Models.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock AccountDao accounts;
    @Mock TransferDao transfers;
    TransferService service;
    UUID key;
    @BeforeEach void setup() { service = new TransferService(accounts, transfers); key = UUID.randomUUID(); }
    private void balances(String first, String second) {
        when(accounts.lock(1)).thenReturn(Optional.of(new Account(1, new BigDecimal(first), BigDecimal.TEN)));
        when(accounts.lock(2)).thenReturn(Optional.of(new Account(2, new BigDecimal(second), BigDecimal.TEN)));
    }
    @Test void debitsAndCreditsExactAmount() {
        balances("100.00", "50.00");
        Transfer result = service.transfer(1,2,new BigDecimal("12.34"),key);
        verify(accounts).updateBalance(1,new BigDecimal("87.66"));
        verify(accounts).updateBalance(2,new BigDecimal("62.34"));
        verify(transfers).insert(result);
        assertThat(result.value).isEqualByComparingTo("12.34");
        assertThat(result.fromUserId).isEqualTo(1);
    }
    @Test void oppositeTransferLocksSmallerIdFirst() {
        balances("100.00", "100.00");
        service.transfer(2,1,BigDecimal.TEN,key);
        InOrder order = inOrder(accounts);
        order.verify(accounts).lock(1);
        order.verify(accounts).lock(2);
        verify(accounts).updateBalance(2,new BigDecimal("90.00"));
    }
    @Test void fullBalanceCanBeTransferred() {
        balances("100.00", "0.00");
        service.transfer(1,2,new BigDecimal("100.00"),key);
        verify(accounts).updateBalance(1,new BigDecimal("0.00"));
    }
    @Test void insufficientFundsDoNotWrite() {
        balances("1.00", "0.00");
        assertThatThrownBy(() -> service.transfer(1,2,BigDecimal.TEN,key)).isInstanceOf(BusinessException.class).hasMessage("Insufficient funds");
        verify(accounts,never()).updateBalance(anyLong(),any());
        verify(transfers,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "0.001", "1.001", "100000000000000000.00"})
    void invalidAmountDoesNotAcquireLocks(String value) {
        assertThatThrownBy(() -> service.transfer(1,2,new BigDecimal(value),key)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(accounts,transfers);
    }
    @Test void missingAndInvalidArgumentsAreRejected() {
        assertThatThrownBy(() -> service.transfer(1,1,BigDecimal.ONE,key)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.transfer(1,0,BigDecimal.ONE,key)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.transfer(1,2,null,key)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.transfer(1,2,BigDecimal.ONE,null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(accounts,transfers);
    }
    @Test void missingAccountDoesNotWrite() {
        when(accounts.lock(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(1,2,BigDecimal.ONE,key)).isInstanceOf(BusinessException.class);
        verify(accounts,never()).updateBalance(anyLong(),any());
    }
    @Test void recipientOverflowDoesNotWrite() {
        balances("100.00", "99999999999999999.99");
        assertThatThrownBy(() -> service.transfer(1,2,BigDecimal.ONE,key)).isInstanceOf(BusinessException.class);
        verify(accounts,never()).updateBalance(anyLong(),any());
    }
    @Test void repeatedRequestReturnsOriginalWithoutCheckingCurrentFunds() {
        balances("0.00", "100.00");
        Transfer original = new Transfer(UUID.randomUUID(),key,1,2,BigDecimal.TEN,Instant.now());
        when(transfers.find(1,key)).thenReturn(Optional.of(original));
        assertThat(service.transfer(1,2,BigDecimal.TEN,key)).isSameAs(original);
        verify(accounts,never()).updateBalance(anyLong(),any());
        verify(transfers,never()).insert(any());
    }
    @Test void reusedKeyWithDifferentAmountIsRejected() {
        balances("100.00", "100.00");
        when(transfers.find(1,key)).thenReturn(Optional.of(new Transfer(UUID.randomUUID(),key,1,2,BigDecimal.ONE,Instant.now())));
        assertThatThrownBy(() -> service.transfer(1,2,BigDecimal.TEN,key)).isInstanceOf(BusinessException.class);
        verify(accounts,never()).updateBalance(anyLong(),any());
    }
}
